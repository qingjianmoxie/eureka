package com.netflix.eureka2.integration.server.registration;

import java.util.Arrays;
import java.util.List;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import rx.Subscription;
import rx.subjects.BehaviorSubject;

import static com.netflix.eureka2.interests.ChangeNotifications.dataOnlyFilter;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.deleteChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.modifyChangeNotificationOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author David Liu
 */
@Category(IntegrationTest.class)
public class WriteServerRegistrationTest {

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = new EurekaDeploymentResource(1, 0);


    @Test(timeout = 60000)
    public void testRegistrationLifecycle() throws Exception {
        final EurekaRegistrationClient registrationClient = eurekaDeploymentResource.registrationClientToWriteServer(0);
        final EurekaInterestClient interestClient = eurekaDeploymentResource.interestClientToWriteServer(0);

        InstanceInfo.Builder seedBuilder = new InstanceInfo.Builder().withId("id").withApp("app");
        List<InstanceInfo> infos = Arrays.asList(
                seedBuilder.withAppGroup("AAA").build(),
                seedBuilder.withAppGroup("BBB").build(),
                seedBuilder.withAppGroup("CCC").build()
        );

        // Subscribe to second write server
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        interestClient.forInterest(Interests.forApplications(infos.get(0).getApp())).filter(dataOnlyFilter()).subscribe(testSubscriber);

        // We need to block, otherwise if we shot all of them in one row, they may be
        // compacted in the index.
        BehaviorSubject<InstanceInfo> registrant = BehaviorSubject.create();
        Subscription subscription = registrationClient.register(registrant).subscribe();
        registrant.onNext(infos.get(0));
        assertThat(testSubscriber.takeNextOrWait(), is(addChangeNotificationOf(infos.get(0))));

        registrant.onNext(infos.get(1));
        assertThat(testSubscriber.takeNextOrWait(), is(modifyChangeNotificationOf(infos.get(1))));

        registrant.onNext(infos.get(2));
        assertThat(testSubscriber.takeNextOrWait(), is(modifyChangeNotificationOf(infos.get(2))));

        subscription.unsubscribe();
        assertThat(testSubscriber.takeNextOrWait(), is(deleteChangeNotificationOf(infos.get(2))));

        registrationClient.shutdown();
        interestClient.shutdown();
    }
}