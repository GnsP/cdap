/*
 * Copyright © 2023 Cask Data, Inc.
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

package io.cdap.cdap.internal.operation;

import io.cdap.cdap.proto.id.OperationRunId;
import io.cdap.cdap.proto.operation.OperationMeta;
import io.cdap.cdap.proto.operation.OperationResource;
import io.cdap.cdap.proto.operation.OperationType;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SynchronousLongRunningOperationContext extends AbstractLongRunningOperationContext {
  private OperationMeta operationMeta;

  public SynchronousLongRunningOperationContext(String namespace, OperationType operationType) {
    super(new OperationRunId(namespace, UUID.randomUUID().toString()), operationType);
    this.operationMeta = new OperationMeta(new HashSet<>(), Instant.now(), null);
  }

  @Override
  public void updateOperationResources(Set<OperationResource> resources) {
    operationMeta = new OperationMeta(resources, operationMeta.getCreateTime(), Instant.now());
  }

  public OperationMeta getOperationMeta() {
    return operationMeta;
  }
}
