/*
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package com.uber.cadence.samples.hello;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.workflow.ActivityOptions;
import com.uber.cadence.workflow.Functions;
import com.uber.cadence.workflow.RetryOptions;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;

import java.time.Duration;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

/**
 * Hello World Cadence workflow that retries an activity using exponential backoff algorithm.
 * Requires a local instance of Cadence server running.
 */
public class HelloActivityRetry {

    private static final String TASK_LIST = "HelloActivity";

    /**
     * Workflow interface has to have at least one method annotated with @WorkflowMethod.
     */
    public interface GreetingWorkflow {
        /**
         * @return greeting string
         */
        @WorkflowMethod
        String getGreeting(String name);
    }

    /**
     * Activity interface is just a POJI
     */
    public interface GreetingActivities {
        String composeGreeting(String greeting, String name);
    }

    /**
     * GreetingWorkflow implementation that calls GreetingsActivities#printIt.
     */
    public static class GreetingWorkflowImpl implements GreetingWorkflow {

        /**
         * To enable activity retry set {@link RetryOptions} on {@link ActivityOptions}.
         * It also works for activities invoked through
         * {@link com.uber.cadence.workflow.Async#invoke(Functions.Proc)} and for child workflows.
         */
        private final GreetingActivities activities = Workflow.newActivityStub(
                GreetingActivities.class,
                new ActivityOptions.Builder()
                        .setScheduleToCloseTimeoutSeconds(10)
                        .setRetryOptions(new RetryOptions.Builder()
                                .setInitialInterval(Duration.ofSeconds(1))
                                .setExpiration(Duration.ofMinutes(1))
                                .build())
                        .build());

        @Override
        public String getGreeting(String name) {
            // This is blocking call that returns only after activity is completed.
            return activities.composeGreeting("Hello", name);
        }
    }

    private static class GreetingActivitiesImpl implements GreetingActivities {
        private int callCount;
        private long lastInvocationTime;

        @Override
        public String composeGreeting(String greeting, String name) {
            if (lastInvocationTime != 0) {
                long timeSinceLastInvocation = System.currentTimeMillis() - lastInvocationTime;
                System.out.print(timeSinceLastInvocation + " milliseconds since last invocation. ");
            }
            lastInvocationTime = System.currentTimeMillis();
            if (++callCount < 4) {
                System.out.println("composeGreeting activity is going to fail");
                throw new IllegalStateException("not yet");
            }
            System.out.println("composeGreeting activity is going to complete");
            return greeting + " " + name + "!";
        }
    }

    public static void main(String[] args) {
        // Start a worker that hosts both workflow and activity implementation
        Worker worker = new Worker(DOMAIN, TASK_LIST);
        // Workflows are stateful. So need a type to create instances.
        worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
        // Activities are stateless and thread safe. So a shared instance is used.
        worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
        // Start listening to the workflow and activity task lists.
        worker.start();

        // Start a workflow execution. Usually it is done from another program.
        WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
        // Get a workflow stub using the same task list the worker uses.
        WorkflowOptions workflowOptions = new WorkflowOptions.Builder()
                .setTaskList(TASK_LIST)
                .setExecutionStartToCloseTimeoutSeconds(30)
                .build();
        GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class,
                workflowOptions);
        // Execute a workflow waiting for it complete.
        String greeting = workflow.getGreeting("World");
        System.out.println(greeting);
        System.exit(0);
    }
}
