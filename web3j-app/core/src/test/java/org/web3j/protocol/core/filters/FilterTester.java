package org.web3j.protocol.core.filters;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import rx.Observable;
import rx.Subscription;

import org.web3j.protocol.ObjectMapperFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EaiFilter;
import org.web3j.protocol.core.methods.response.EaiLog;
import org.web3j.protocol.core.methods.response.EaiUninstallFilter;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class FilterTester {

    private Web3jService web3jService;
    Web3j web3j;

    final ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
    final ScheduledExecutorService scheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor();

    @Before
    public void setUp() {
        web3jService = mock(Web3jService.class);
        web3j = Web3j.build(web3jService, 1000, scheduledExecutorService);
    }

    <T> void runTest(EaiLog eaiLog, Observable<T> observable) throws Exception {
        EaiFilter eaiFilter = objectMapper.readValue(
                "{\n"
                        + "  \"id\":1,\n"
                        + "  \"jsonrpc\": \"2.0\",\n"
                        + "  \"result\": \"0x1\"\n"
                        + "}", EaiFilter.class);

        EaiUninstallFilter eaiUninstallFilter = objectMapper.readValue(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":true}", EaiUninstallFilter.class);

        @SuppressWarnings("unchecked")
        List<T> expected = createExpected(eaiLog);
        Set<T> results = Collections.synchronizedSet(new HashSet<T>());

        CountDownLatch transactionLatch = new CountDownLatch(expected.size());

        CountDownLatch completedLatch = new CountDownLatch(1);

        when(web3jService.send(any(Request.class), eq(EaiFilter.class)))
                .thenReturn(eaiFilter);
        when(web3jService.send(any(Request.class), eq(EaiLog.class)))
                .thenReturn(eaiLog);
        when(web3jService.send(any(Request.class), eq(EaiUninstallFilter.class)))
                .thenReturn(eaiUninstallFilter);

        Subscription subscription = observable.subscribe(
                result -> {
                    results.add(result);
                    transactionLatch.countDown();
                },
                throwable -> fail(throwable.getMessage()),
                () -> completedLatch.countDown());

        transactionLatch.await(1, TimeUnit.SECONDS);
        assertThat(results, equalTo(new HashSet<>(expected)));

        subscription.unsubscribe();

        completedLatch.await(1, TimeUnit.SECONDS);
        assertTrue(subscription.isUnsubscribed());
    }

    List createExpected(EaiLog eaiLog) {
        List<EaiLog.LogResult> logResults = eaiLog.getLogs();
        if (logResults.isEmpty()) {
            fail("Results cannot be empty");
        }

        return eaiLog.getLogs().stream()
                .map(t -> t.get()).collect(Collectors.toList());
    }
}
