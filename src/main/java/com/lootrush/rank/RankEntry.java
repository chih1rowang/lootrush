package com.lootrush.rank;

/**
 * 排行榜条目（对外展示用）
 */
public record RankEntry(int rank, Long userId, String name, long score) {
}
