package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.model.TimeoutMultiplier;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.When;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

@ScenarioScoped
public class WaitSteps {
    private static final long DEFAULT_INTERVAL = 100L;
    private final TimeoutMultiplier multiplier;

    @Inject
    public WaitSteps(TimeoutMultiplier multiplier) {
        this.multiplier = multiplier;
    }

    @When("I wait {int} {word}")
    public void until(int value, String unit) throws InterruptedException {
        Thread.sleep(TimeUnit.valueOf(unit.toUpperCase()).toMillis(multiplier.multiply(value)));
    }

    public <T> boolean untilTerminal(
            Supplier<T> obtain,
            Predicate<T> isValid,
            Predicate<T> isTerminal,
            int value, TimeUnit unit) throws InterruptedException {
         boolean result = untilTrue(() -> isTerminal.test(obtain.get()), value, unit);
         return result && isValid.test(obtain.get());
    }

    public boolean untilTrue(Supplier<Boolean> evaluate, int value, TimeUnit unit) throws InterruptedException {
        boolean result = false;
        long startTime = System.currentTimeMillis();
        do {
            result = evaluate.get();
            if (result) {
                break;
            }
            Thread.sleep(DEFAULT_INTERVAL);
        } while (System.currentTimeMillis() - startTime < unit.toMillis(multiplier.multiply(value)));
        return result;
    }
}
