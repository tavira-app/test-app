package ir.khodroban.price;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PriceStatistics {
    private PriceStatistics() {}

    public static Summary summarize(List<Long> source) {
        if (source.isEmpty()) return new Summary(List.of(), null, null, null, null);
        List<Long> values = new ArrayList<>(source);
        values.sort(Comparator.naturalOrder());
        List<Long> retained = retainNonOutliers(values);
        if (retained.isEmpty()) retained = values;
        return new Summary(retained, median(retained), percentile(retained, 25),
                percentile(retained, 75), BigDecimal.valueOf(retained.size()));
    }

    /**
     * Uses a median/MAD fence rather than a quartile fence. For small, skewed
     * samples, Tukey fences can classify a legitimate high observation as an
     * outlier because the outlier itself shifts Q3. The MAD remains stable
     * when one extreme asking price is present.
     */
    private static List<Long> retainNonOutliers(List<Long> sorted) {
        if (sorted.size() < 4) return sorted;
        BigDecimal centre = median(sorted);
        List<Long> deviations = sorted.stream()
                .map(value -> BigDecimal.valueOf(value)
                        .subtract(centre).abs()
                        .setScale(2, RoundingMode.HALF_UP)
                        .longValue())
                .sorted()
                .toList();
        BigDecimal mad = median(deviations);
        BigDecimal minimumScale = centre.abs()
                .multiply(BigDecimal.valueOf(0.05))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal scale = mad.max(minimumScale);
        BigDecimal lower = centre.subtract(scale.multiply(BigDecimal.valueOf(3)));
        BigDecimal upper = centre.add(scale.multiply(BigDecimal.valueOf(3)));
        return sorted.stream()
                .filter(value -> {
                    BigDecimal amount = BigDecimal.valueOf(value);
                    return amount.compareTo(lower) >= 0 && amount.compareTo(upper) <= 0;
                })
                .toList();
    }

    private static BigDecimal median(List<Long> sorted) {
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return BigDecimal.valueOf(sorted.get(middle)).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(sorted.get(middle - 1))
                .add(BigDecimal.valueOf(sorted.get(middle)))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentile(List<Long> sorted, int percentile) {
        if (sorted.isEmpty()) return null;
        if (sorted.size() == 1) return BigDecimal.valueOf(sorted.getFirst()).setScale(2);
        BigDecimal position = BigDecimal.valueOf(percentile)
                .multiply(BigDecimal.valueOf(sorted.size() - 1))
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        int lower = position.intValue();
        int upper = Math.min(lower + 1, sorted.size() - 1);
        BigDecimal fraction = position.subtract(BigDecimal.valueOf(lower));
        BigDecimal lowerValue = BigDecimal.valueOf(sorted.get(lower));
        BigDecimal upperValue = BigDecimal.valueOf(sorted.get(upper));
        return lowerValue.add(
                upperValue.subtract(lowerValue).multiply(fraction))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public record Summary(List<Long> retained, BigDecimal median, BigDecimal p25,
                          BigDecimal p75, BigDecimal retainedCount) {}
}
