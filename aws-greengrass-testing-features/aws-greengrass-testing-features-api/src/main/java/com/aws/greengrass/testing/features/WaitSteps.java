package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.model.TimeoutMultiplier;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.When;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.inject.Inject;

@ScenarioScoped
public class WaitSteps {
    private static final long DEFAULT_INTERVAL = 100L;
    private final TimeoutMultiplier multiplier;

    @Inject
    WaitSteps(TimeoutMultiplier multiplier) {
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

    /**
     * Wait until the evaluated predicate is true for a time.
     *
     * @param evaluate {@link Predicate} to evaluate
     * @param value integer for a duration
     * @param unit {@link TimeUnit} duration
     * @return boolean
     * @throws InterruptedException thread interrupted while waiting
     */
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
