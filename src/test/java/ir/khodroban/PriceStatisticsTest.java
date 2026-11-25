package ir.khodroban;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ir.khodroban.price.PriceStatistics;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceStatisticsTest {
    @Test
    void interpolatesPercentilesAndRemovesExtremeOutlier() {
        PriceStatistics.Summary summary = PriceStatistics.summarize(
                List.of(100L, 110L, 120L, 130L, 10000L));
        assertEquals(new BigDecimal("115.00"), summary.median());
        assertEquals(new BigDecimal("107.50"), summary.p25());
        assertEquals(new BigDecimal("122.50"), summary.p75());
        assertEquals(List.of(100L, 110L, 120L, 130L), summary.retained());
        assertEquals(4, summary.retained().size());
    }

    @Test
    void emptyAndSmallSamplesRemainDeterministic() {
        assertEquals(null, PriceStatistics.summarize(List.of()).median());
        assertEquals(new BigDecimal("115.00"),
                PriceStatistics.percentile(List.of(100L, 130L), 50));
    }
}
