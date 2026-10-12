package com.ieia.demo;

import com.ieia.activity.ActivityType;
import com.ieia.activity.ActivityTypeRepository;
import com.ieia.anomaly.AnomalyService;
import com.ieia.auth.AuthService;
import com.ieia.auth.dto.RegisterRequest;
import com.ieia.config.AppProperties;
import com.ieia.prediction.PredictionService;
import com.ieia.recommendation.RecommendationService;
import com.ieia.record.RecordService;
import com.ieia.record.dto.RecordRequest;
import com.ieia.security.Role;
import com.ieia.user.User;
import com.ieia.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * On startup, when {@code ieia.seed.demo=true} and the database has no users,
 * builds a realistic multi-month dataset so every feature is demonstrable
 * immediately (brief §21). Records go through {@link RecordService}, so the full
 * impact → metric → score pipeline runs for each one.
 *
 * <p>Intentional patterns for the ML features:
 * <ul>
 *   <li>a rising electricity trend with a summer seasonal bump;</li>
 *   <li>one sharp electricity <b>spike</b> (~+40%) and one water <b>drop</b>;</li>
 *   <li>a gradual increase in recycling rate;</li>
 *   <li>a transport step-change (a new commute) partway through.</li>
 * </ul>
 */
@Component
@Order(50)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final int MONTHS = 24;
    static final String PASSWORD = "Demo!Pass1";

    private final AppProperties props;
    private final UserRepository userRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final AuthService authService;
    private final RecordService recordService;
    private final PredictionService predictionService;
    private final AnomalyService anomalyService;
    private final RecommendationService recommendationService;
    private final Map<String, UUID> activityIdCache = new ConcurrentHashMap<>();

    public DemoDataSeeder(AppProperties props,
                          UserRepository userRepository,
                          ActivityTypeRepository activityTypeRepository,
                          AuthService authService,
                          RecordService recordService,
                          PredictionService predictionService,
                          AnomalyService anomalyService,
                          RecommendationService recommendationService) {
        this.props = props;
        this.userRepository = userRepository;
        this.activityTypeRepository = activityTypeRepository;
        this.authService = authService;
        this.recordService = recordService;
        this.predictionService = predictionService;
        this.anomalyService = anomalyService;
        this.recommendationService = recommendationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.getSeed().isDemo() || userRepository.count() > 0) {
            if (props.getSeed().isDemo()) log.info("Demo seed skipped — users already present");
            return;
        }
        long started = System.currentTimeMillis();
        log.info("Seeding demo data ({} months of history)…", MONTHS);

        registerAndPromoteAdmin();

        UUID alexId = registerIndividual("alex@ieia.demo", "Alex Rivera", "IN");
        seedIndividualHistory(alexId, new Random(42));

        UUID orgOwnerId = registerOrganization("owner@greenfield.demo", "Sam Okoye", "Greenfield Labs", 60, "GLOBAL");
        seedOrganizationHistory(orgOwnerId, new Random(7));

        try {
            for (String metric : List.of("CO2E", "ENERGY", "WATER", "SCORE")) {
                predictionService.getOrCreate(alexId, metric, 1, true);
            }
            anomalyService.scan(alexId, null, null, "MEDIUM");
            recommendationService.generate(alexId, null, false);
        } catch (RuntimeException e) {
            log.warn("Demo seed: ML-backed artefacts skipped ({})", e.getMessage());
        }

        log.info("Demo data ready in {} ms. Logins — admin@ieia.demo · alex@ieia.demo · owner@greenfield.demo  (password: {})",
                System.currentTimeMillis() - started, PASSWORD);
    }

    // ---- accounts --------------------------------------------------

    void registerAndPromoteAdmin() {
        authService.register(new RegisterRequest("admin@ieia.demo", PASSWORD, "IEIA Admin",
                "INDIVIDUAL", null, null, null, "GLOBAL", null), "seeder", "127.0.0.1");
        User admin = userRepository.findByEmailIgnoreCase("admin@ieia.demo").orElseThrow();
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
    }

    private UUID registerIndividual(String email, String name, String region) {
        authService.register(new RegisterRequest(email, PASSWORD, name, "INDIVIDUAL",
                null, null, null, region, null), "seeder", "127.0.0.1");
        return userRepository.findByEmailIgnoreCase(email).map(User::getId).orElseThrow();
    }

    private UUID registerOrganization(String email, String name, String orgName, int headcount, String region) {
        authService.register(new RegisterRequest(email, PASSWORD, name, "ORGANIZATION",
                orgName, "Research", "US", region, headcount), "seeder", "127.0.0.1");
        return userRepository.findByEmailIgnoreCase(email).map(User::getId).orElseThrow();
    }

    // ---- history --------------------------------------------------

    private void seedIndividualHistory(UUID userId, Random rnd) {
        YearMonth start = YearMonth.now().minusMonths(MONTHS);
        int spikeMonth = MONTHS - 9;
        int waterDropMonth = MONTHS - 6;
        int commuteChange = MONTHS / 2;

        for (int i = 0; i < MONTHS; i++) {
            YearMonth ym = start.plusMonths(i);
            String period = ym.toString();
            double seasonal = 90 * Math.sin((ym.getMonthValue() - 4) / 12.0 * 2 * Math.PI);
            double electricity = 320 + i * 6.5 + seasonal + rnd.nextGaussian() * 14 + (i == spikeMonth ? 300 : 0);
            add(userId, "electricity_grid", period, electricity, "kWh");
            add(userId, "lpg", period, 9 + rnd.nextGaussian() * 1.2, "kg");

            if (i < commuteChange) {
                add(userId, "car_petrol", period, 420 + rnd.nextGaussian() * 40, "km");
            } else {
                add(userId, "car_petrol", period, 120 + rnd.nextGaussian() * 25, "km");
                add(userId, "metro_rail", period, 360 + rnd.nextGaussian() * 30, "km");
            }

            add(userId, "municipal_water", period, 8200 + rnd.nextGaussian() * 200 - (i == waterDropMonth ? 3600 : 0), "litre");

            double recycledFrac = Math.min(0.75, 0.20 + i * 0.02);
            double totalWaste = 62 + rnd.nextGaussian() * 4;
            add(userId, "waste_mixed", period, totalWaste * (1 - recycledFrac), "kg");
            add(userId, "waste_recycled", period, totalWaste * recycledFrac, "kg");

            if (i % 4 == 2) add(userId, "material_paper", period, 3 + rnd.nextDouble() * 2, "kg");
        }
    }

    private void seedOrganizationHistory(UUID userId, Random rnd) {
        YearMonth start = YearMonth.now().minusMonths(MONTHS);
        for (int i = 0; i < MONTHS; i++) {
            YearMonth ym = start.plusMonths(i);
            String period = ym.toString();
            double seasonal = 1400 * Math.sin((ym.getMonthValue() - 3) / 12.0 * 2 * Math.PI);
            add(userId, "electricity_grid", period, 9000 - i * 60 + seasonal + rnd.nextGaussian() * 300, "kWh");
            add(userId, "diesel", period, 140 + rnd.nextGaussian() * 20, "litre");
            add(userId, "natural_gas", period, 300 + rnd.nextGaussian() * 40, "m3");
            add(userId, "municipal_water", period, 52000 + rnd.nextGaussian() * 3000, "litre");
            add(userId, "waste_mixed", period, 380 - i * 3 + rnd.nextGaussian() * 20, "kg");
            add(userId, "waste_recycled", period, 90 + i * 3 + rnd.nextGaussian() * 10, "kg");
            add(userId, "material_steel", period, 40 + rnd.nextGaussian() * 8, "kg");
            if (i % 3 == 0) add(userId, "domestic_flight", period, 2400 + rnd.nextGaussian() * 400, "km");
        }
    }

    private void add(UUID userId, String activityCode, String period, double amount, String unit) {
        UUID activityTypeId = activityIdCache.computeIfAbsent(activityCode,
                c -> activityTypeRepository.findByCode(c).map(ActivityType::getId).orElse(null));
        if (activityTypeId == null) return;
        BigDecimal amt = BigDecimal.valueOf(Math.max(0.0, amount)).setScale(2, RoundingMode.HALF_UP);
        if (amt.signum() <= 0) return;
        try {
            recordService.create(userId, new RecordRequest(activityTypeId, period, amt, unit, null, null, null, null));
        } catch (RuntimeException e) {
            log.debug("Demo seed: record {} {} skipped ({})", activityCode, period, e.getMessage());
        }
    }
}
