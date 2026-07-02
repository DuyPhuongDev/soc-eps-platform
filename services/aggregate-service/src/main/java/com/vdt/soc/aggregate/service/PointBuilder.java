package com.vdt.soc.aggregate.service;

@FunctionalInterface
public interface PointBuilder<T> {
    T build(long bucketStartMs, long acceptedSum, long droppedSum, int rowCount);
}