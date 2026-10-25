package com.lootrush.goods;

import com.lootrush.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 商品服务：上架、改价（延迟双删）、货架查询（缓存）。
 *
 * 缓存策略（对应技术总表场景3/4）：
 * - 货架整架缓存为一条 JSON，TTL 在 5~7 分钟里随机——防止同一秒集体过期（雪崩）；
 * - 空结果也缓存 60 秒（穿透防护：恶意刷不存在的货架不再打穿到库）；
 * - 改价时"先删缓存、更新库、500ms 后再删一次"（延迟双删）：
 *   第一次删之后到数据库更新完成之间，可能有线程刚读到旧库值并把它写回缓存，
 *   第二次延迟删除专门清掉这份"脏缓存"。
 */
@Service
public class GoodsService {

    public static final String LIST_KEY = "goods:list";
    public static final String STOCK_KEY_PREFIX = "stock:";

    private static final Logger log = LoggerFactory.getLogger(GoodsService.class);

    private final ItemMapper itemMapper;
    private final GoodsMapper goodsMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoodsService(ItemMapper itemMapper, GoodsMapper goodsMapper, StringRedisTemplate redis) {
        this.itemMapper = itemMapper;
        this.goodsMapper = goodsMapper;
        this.redis = redis;
    }

    /** 上架：建道具 + 建商品 + 库存预热进 Redis + 立刻作废货架缓存 */
    public Long createGoods(String name, String description, String imageUrl,
                            int price, int originalPrice, int stock,
                            LocalDateTime saleStart, LocalDateTime saleEnd) {
        if (!saleStart.isBefore(saleEnd)) {
            throw BizException.of(400, "开售时间必须早于结束时间");
        }
        ItemEntity item = new ItemEntity();
        item.setName(name);
        item.setRarity(1);
        item.setSourceType("SHOP");
        item.setDescription(description);
        item.setImageUrl(imageUrl);
        itemMapper.insert(item);

        GoodsEntity goods = new GoodsEntity();
        goods.setItemId(item.getId());
        goods.setPriceDiamond(price);
        goods.setOriginalPrice(originalPrice);
        goods.setStockTotal(stock);
        goods.setSaleStartTime(saleStart);
        goods.setSaleEndTime(saleEnd);
        goodsMapper.insert(goods);

        redis.opsForValue().set(STOCK_KEY_PREFIX + goods.getId(), String.valueOf(stock));
        redis.delete(LIST_KEY);
        log.info("上架商品 id={} name={} 库存预热={} 货架缓存已作废", goods.getId(), name, stock);
        return goods.getId();
    }

    /** 改价：更新库 + 延迟双删 */
    public void updatePrice(Long goodsId, int newPrice) {
        if (goodsMapper.selectById(goodsId) == null) {
            throw BizException.of(404, "商品不存在");
        }
        goodsMapper.updatePrice(goodsId, newPrice);
        redis.delete(LIST_KEY); // 第一次删：让新请求去读新库值
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(500); // 等可能正读到旧库值的请求写完它的脏缓存
                redis.delete(LIST_KEY); // 第二次删：清掉那份脏缓存
                log.info("延迟双删完成 goodsId={}", goodsId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** 玩家货架：四态 + 倒计时 + 实时剩余库存（Redis 优先，缺了回源并回填） */
    public List<GoodsVO> listGoods() {
        String cached = redis.opsForValue().get(LIST_KEY);
        if (cached != null) {
            return parseList(cached);
        }
        List<GoodsFlat> flats = goodsMapper.selectAllWithItem();
        LocalDateTime now = LocalDateTime.now();
        List<GoodsVO> vo = flats.stream().map(f -> toVO(f, now)).toList();

        if (vo.isEmpty()) {
            redis.opsForValue().set(LIST_KEY, "[]", Duration.ofSeconds(60)); // 空值缓存防穿透
        } else {
            long ttl = 300 + (long) (Math.random() * 120); // 5~7分钟随机，防雪崩
            redis.opsForValue().set(LIST_KEY, writeList(vo), Duration.ofSeconds(ttl));
        }
        return vo;
    }

    /** 拿实时剩余库存：Redis 有用 Redis，没有回源数据库并回填 */
    public long stockLeftOf(Long goodsId, int stockTotalInDb) {
        String v = redis.opsForValue().get(STOCK_KEY_PREFIX + goodsId);
        if (v != null) {
            return Long.parseLong(v);
        }
        redis.opsForValue().set(STOCK_KEY_PREFIX + goodsId, String.valueOf(stockTotalInDb));
        return stockTotalInDb;
    }

    private GoodsVO toVO(GoodsFlat f, LocalDateTime now) {
        long stockLeft = stockLeftOf(f.getId(), f.getStockTotal());
        String status;
        Long countdownSec = null;
        if (now.isBefore(f.getSaleStartTime())) {
            status = "UPCOMING";
            countdownSec = Duration.between(now, f.getSaleStartTime()).getSeconds();
        } else if (now.isAfter(f.getSaleEndTime())) {
            status = "ENDED";
        } else {
            status = stockLeft > 0 ? "ON_SALE" : "SOLD_OUT";
            countdownSec = Duration.between(now, f.getSaleEndTime()).getSeconds();
        }
        return new GoodsVO(f.getId(), f.getName(), f.getDescription(), f.getImageUrl(),
                f.getPriceDiamond(), f.getOriginalPrice(), stockLeft, status, countdownSec);
    }

    private List<GoodsVO> parseList(String json) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, GoodsVO.class));
        } catch (Exception e) {
            log.warn("货架缓存内容损坏，按未命中处理", e);
            return null;
        }
    }

    private String writeList(List<GoodsVO> vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (Exception e) {
            throw new IllegalStateException("货架序列化失败", e);
        }
    }

    /** 货架展示对象（record：不可变，字段即接口出参） */
    public record GoodsVO(Long id, String name, String description, String imageUrl,
                          int priceDiamond, int originalPrice, long stockLeft,
                          String status, Long countdownSec) {
    }
}
