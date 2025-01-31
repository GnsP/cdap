/*
 * Copyright © 2015-2016 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.cdap.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import io.cdap.cdap.AppWithWorkflow;
import io.cdap.cdap.api.workflow.NodeStatus;
import io.cdap.cdap.api.workflow.WorkflowToken;
import io.cdap.cdap.client.common.ClientTestBase;
import io.cdap.cdap.common.NotFoundException;
import io.cdap.cdap.common.app.RunIds;
import io.cdap.cdap.proto.DatasetSpecificationSummary;
import io.cdap.cdap.proto.ProgramRunStatus;
import io.cdap.cdap.proto.ProgramType;
import io.cdap.cdap.proto.RunRecord;
import io.cdap.cdap.proto.WorkflowNodeStateDetail;
import io.cdap.cdap.proto.WorkflowTokenDetail;
import io.cdap.cdap.proto.WorkflowTokenNodeDetail;
import io.cdap.cdap.proto.id.ApplicationId;
import io.cdap.cdap.proto.id.NamespaceId;
import io.cdap.cdap.proto.id.ProgramId;
import io.cdap.cdap.proto.id.ProgramRunId;
import io.cdap.cdap.proto.id.WorkflowId;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link WorkflowClient}
 */
public class WorkflowClientTestRun extends ClientTestBase {

  private static final ApplicationId appId = new ApplicationId(NamespaceId.DEFAULT.getNamespace(),
                                                               AppWithWorkflow.NAME);
  private ApplicationClient appClient;
  private ProgramClient programClient;
  private WorkflowClient workflowClient;

  @Before
  public void setUp() throws Throwable {
    super.setUp();
    appClient = new ApplicationClient(clientConfig);
    programClient = new ProgramClient(clientConfig);
    workflowClient = new WorkflowClient(clientConfig);
    appClient.deploy(NamespaceId.DEFAULT, createAppJarFile(AppWithWorkflow.class));
  }

  @After
  public void tearDown() throws Exception {
    appClient.deleteApp(appId);
  }

  @Test
  public void testWorkflowClient() throws Exception {
    String keyValueTableType = "io.cdap.cdap.api.dataset.lib.KeyValueTable";
    String filesetType = "io.cdap.cdap.api.dataset.lib.FileSet";

    String outputPath = new File(TMP_FOLDER.newFolder(), "output").getAbsolutePath();
    Map<String, String> runtimeArgs = ImmutableMap.of("inputPath", createInput("input"),
                                                      "outputPath", outputPath, "dataset.*.keep.local", "true");
    final WorkflowId workflowId =
      NamespaceId.DEFAULT.app(AppWithWorkflow.NAME).workflow(AppWithWorkflow.SampleWorkflow.NAME);

    programClient.start(workflowId, false, runtimeArgs);
    assertProgramRuns(programClient, workflowId, ProgramRunStatus.COMPLETED, 1, 120);

    List<RunRecord> workflowRuns = programClient.getProgramRuns(workflowId, ProgramRunStatus.COMPLETED.name(), 0,
                                                                Long.MAX_VALUE, 10);
    Assert.assertEquals(1, workflowRuns.size());

    String runId = workflowRuns.get(0).getPid();
    ProgramRunId workflowRunId = workflowId.run(runId);

    // Invalid test scenarios
    try {
      ProgramId nonExistentWorkflowId = new ProgramId(NamespaceId.DEFAULT.getNamespace(), AppWithWorkflow.NAME,
                                                      ProgramType.WORKFLOW, "NonExistentWorkflow");
      ProgramRunId nonExistentWorkflowRun = nonExistentWorkflowId.run(runId);
      workflowClient.getWorkflowToken(nonExistentWorkflowRun);
      Assert.fail("Should not find a workflow token for a non-existing workflow");
    } catch (NotFoundException expected) {
      // expected
    }
    try {
      ProgramRunId invalidRunId = workflowId.run(RunIds.generate().getId());
      workflowClient.getWorkflowToken(invalidRunId);
      Assert.fail("Should not find a workflow token for a random run id");
    } catch (NotFoundException expected) {
      // expected
    }

    // Valid test scenarios
    WorkflowTokenDetail workflowToken = workflowClient.getWorkflowToken(workflowRunId);
    Assert.assertEquals(5, workflowToken.getTokenData().size());
    workflowToken = workflowClient.getWorkflowToken(workflowRunId, WorkflowToken.Scope.SYSTEM);
    Assert.assertTrue(workflowToken.getTokenData().size() > 0);
    workflowToken = workflowClient.getWorkflowToken(workflowRunId, "start_time");
    Map<String, List<WorkflowTokenDetail.NodeValueDetail>> tokenData = workflowToken.getTokenData();
    Assert.assertEquals(AppWithWorkflow.WordCountMapReduce.NAME, tokenData.get("start_time").get(0).getNode());
    Assert.assertTrue(Long.parseLong(tokenData.get("start_time").get(0).getValue()) < System.currentTimeMillis());
    workflowToken = workflowClient.getWorkflowToken(workflowRunId, WorkflowToken.Scope.USER, "action_type");
    tokenData = workflowToken.getTokenData();
    Assert.assertEquals(AppWithWorkflow.WordCountMapReduce.NAME, tokenData.get("action_type").get(0).getNode());
    Assert.assertEquals("MapReduce", tokenData.get("action_type").get(0).getValue());
    String nodeName = AppWithWorkflow.SampleWorkflow.FIRST_ACTION;
    WorkflowTokenNodeDetail workflowTokenAtNode =
      workflowClient.getWorkflowTokenAtNode(workflowRunId, nodeName);
    Assert.assertEquals(AppWithWorkflow.DummyAction.TOKEN_VALUE,
                        workflowTokenAtNode.getTokenDataAtNode().get(AppWithWorkflow.DummyAction.TOKEN_KEY));
    workflowTokenAtNode = workflowClient.getWorkflowTokenAtNode(workflowRunId, nodeName,
                                                                WorkflowToken.Scope.SYSTEM);
    Assert.assertEquals(0, workflowTokenAtNode.getTokenDataAtNode().size());
    workflowTokenAtNode = workflowClient.getWorkflowTokenAtNode(workflowRunId, nodeName,
                                                                AppWithWorkflow.DummyAction.TOKEN_KEY);
    Assert.assertEquals(AppWithWorkflow.DummyAction.TOKEN_VALUE,
                        workflowTokenAtNode.getTokenDataAtNode().get(AppWithWorkflow.DummyAction.TOKEN_KEY));
    String reduceOutputRecordsCounter = "org.apache.hadoop.mapreduce.TaskCounter.REDUCE_OUTPUT_RECORDS";
    workflowTokenAtNode = workflowClient.getWorkflowTokenAtNode(workflowRunId,
                                                                AppWithWorkflow.WordCountMapReduce.NAME,
                                                                WorkflowToken.Scope.SYSTEM, reduceOutputRecordsCounter);
    Assert.assertEquals(6, Integer.parseInt(workflowTokenAtNode.getTokenDataAtNode().get(reduceOutputRecordsCounter)));

    Map<String, DatasetSpecificationSummary> localDatasetSummaries
      = workflowClient.getWorkflowLocalDatasets(workflowRunId);
    Assert.assertEquals(2, localDatasetSummaries.size());
    DatasetSpecificationSummary keyValueTableSummary = new DatasetSpecificationSummary("MyTable." + runId,
                                                                                       keyValueTableType,
                                                                                       ImmutableMap.of("foo", "bar"));
    Assert.assertEquals(keyValueTableSummary, localDatasetSummaries.get("MyTable"));
    DatasetSpecificationSummary filesetSummary = new DatasetSpecificationSummary("MyFile." + runId, filesetType,
                                                                                 ImmutableMap.of("anotherFoo",
                                                                                                 "anotherBar"));
    Assert.assertEquals(filesetSummary, localDatasetSummaries.get("MyFile"));

    workflowClient.deleteWorkflowLocalDatasets(workflowRunId);

    localDatasetSummaries = workflowClient.getWorkflowLocalDatasets(workflowRunId);
    Assert.assertEquals(0, localDatasetSummaries.size());

    Map<String, WorkflowNodeStateDetail> nodeStates = workflowClient.getWorkflowNodeStates(workflowRunId);
    Assert.assertEquals(3, nodeStates.size());

    WorkflowNodeStateDetail nodeState = nodeStates.get(AppWithWorkflow.SampleWorkflow.FIRST_ACTION);
    Assert.assertTrue(AppWithWorkflow.SampleWorkflow.FIRST_ACTION.equals(nodeState.getNodeId()));
    Assert.assertTrue(NodeStatus.COMPLETED == nodeState.getNodeStatus());

    nodeState = nodeStates.get(AppWithWorkflow.SampleWorkflow.SECOND_ACTION);
    Assert.assertTrue(AppWithWorkflow.SampleWorkflow.SECOND_ACTION.equals(nodeState.getNodeId()));
    Assert.assertTrue(NodeStatus.COMPLETED == nodeState.getNodeStatus());

    nodeState = nodeStates.get(AppWithWorkflow.SampleWorkflow.WORD_COUNT_MR);
    Assert.assertTrue(AppWithWorkflow.SampleWorkflow.WORD_COUNT_MR.equals(nodeState.getNodeId()));
    Assert.assertTrue(NodeStatus.COMPLETED == nodeState.getNodeStatus());
  }

  private String createInput(String folderName) throws IOException {
    File inputDir = TMP_FOLDER.newFolder(folderName);

    File inputFile = new File(inputDir.getPath() + "/words.txt");
    try (BufferedWriter writer = Files.newBufferedWriter(inputFile.toPath(), Charsets.UTF_8)) {
      writer.write("this text has");
      writer.newLine();
      writer.write("two words text inside");
    }
    return inputDir.getAbsolutePath();
  }
}
