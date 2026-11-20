package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.domain.ProvisioningState;
import com.msg.ppap.masterdata.api.OfferReconciler;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * {@link OfferReconciler}: runs the EDC provisioning synchronously after a policy profile or relation write, bounded by
 * {@code ppap.exchange.reconcile-timeout} (default 5 s); a longer run finishes in the background ({@code PENDING}).
 */
@Service
public class OfferReconcilerService implements OfferReconciler {

    private final ProvisioningService provisioning;
    private final Duration timeout;
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    public OfferReconcilerService(
            ProvisioningService provisioning, @Value("${ppap.exchange.reconcile-timeout:5s}") Duration timeout) {
        this.provisioning = provisioning;
        this.timeout = timeout;
    }

    @Override
    public Result reconcileNow() {
        CompletableFuture<ProvisioningState> run = CompletableFuture.supplyAsync(provisioning::reconcile, executor);
        try {
            return result(run.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException ex) {
            return new Result("PENDING", List.of(), List.of(), null);
        } catch (ExecutionException ex) {
            return new Result("FAILED", List.of(), List.of(), String.valueOf(ex.getCause()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result("PENDING", List.of(), List.of(), null);
        }
    }

    static Result result(ProvisioningState state) {
        return state.ok()
                ? new Result("APPLIED", state.policyIds(), state.contractDefinitionIds(), null)
                : new Result("FAILED", List.of(), List.of(), state.error());
    }
}
