package com.uber.cadence.samples.hello;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.samples.hello.HelloActivity.GreetingActivities;
import com.uber.cadence.samples.hello.HelloActivity.GreetingActivitiesImpl;
import com.uber.cadence.samples.hello.HelloActivity.GreetingWorkflow;
import com.uber.cadence.samples.hello.HelloActivity.GreetingWorkflowImpl;
import com.uber.cadence.testing.TestWorkflowEnvironment;
import com.uber.cadence.worker.Worker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HelloActivityTest {

  private TestWorkflowEnvironment testEnv;
  private Worker worker;
  private WorkflowClient workflowClient;

  @Before
  public void setUp() {
    testEnv = TestWorkflowEnvironment.newInstance();
    worker = testEnv.newWorker(HelloActivity.TASK_LIST);
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);

    workflowClient = testEnv.newWorkflowClient();
  }

  @After
  public void tearDown() {
    testEnv.close();
  }

  @Test
  public void testActivityImpl() {
    worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
    worker.start();

    // Get a workflow stub using the same task list the worker uses.
    GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);
    // Execute a workflow waiting for it complete.
    String greeting = workflow.getGreeting("World");
    assertEquals("Hello World!", greeting);
  }

  @Test
  public void testMockedActivity() {
    GreetingActivities activities = mock(GreetingActivities.class);
    when(activities.composeGreeting("Hello", "World")).thenReturn("Hello World!");
    worker.registerActivitiesImplementations(activities);
    worker.start();

    // Get a workflow stub using the same task list the worker uses.
    GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);
    // Execute a workflow waiting for it complete.
    String greeting = workflow.getGreeting("World");
    assertEquals("Hello World!", greeting);
  }
}