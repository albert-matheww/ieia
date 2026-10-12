package com.ieia.benchmark;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BenchmarkRepository extends JpaRepository<Benchmark, UUID> {

    List<Benchmark> findByScopeAndSegmentAndActiveTrue(String scope, String segment);

    @Query("""
           select b from Benchmark b
           where b.scope = :scope and b.segment = :segment and b.metric = :metric
             and b.period = :period and b.active = true
             and (b.region = :region or b.region = 'GLOBAL')
           order by case when b.region = :region then 0 else 1 end, b.effectiveFrom desc
           """)
    List<Benchmark> resolve(@Param("scope") String scope, @Param("segment") String segment,
                            @Param("metric") String metric, @Param("period") String period,
                            @Param("region") String region);

    Optional<Benchmark> findFirstByScopeAndSegmentAndMetricAndPeriodAndRegion(
            String scope, String segment, String metric, String period, String region);
}
