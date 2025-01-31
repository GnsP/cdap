/*
 * Copyright © 2014-2019 Cask Data, Inc.
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

package io.cdap.cdap.cli.command;

import io.cdap.cdap.cli.ArgumentName;
import io.cdap.cdap.cli.CLIConfig;
import io.cdap.cdap.cli.ElementType;
import io.cdap.cdap.cli.english.Article;
import io.cdap.cdap.cli.english.Fragment;
import io.cdap.cdap.cli.exception.CommandInputError;
import io.cdap.cdap.cli.util.AbstractAuthCommand;
import io.cdap.cdap.client.ProgramClient;
import io.cdap.cdap.proto.id.ApplicationId;
import io.cdap.cdap.proto.id.ProgramId;
import io.cdap.common.cli.Arguments;

import java.io.PrintStream;

/**
 * Gets the status of a program.
 */
public class GetProgramStatusCommand extends AbstractAuthCommand {

  private final ProgramClient programClient;
  private final ElementType elementType;

  protected GetProgramStatusCommand(ElementType elementType, ProgramClient programClient, CLIConfig cliConfig) {
    super(cliConfig);
    this.elementType = elementType;
    this.programClient = programClient;
  }

  @Override
  public void perform(Arguments arguments, PrintStream output) throws Exception {
    String[] programIdParts = arguments.get(elementType.getArgumentName().toString()).split("\\.");
    if (programIdParts.length < 2) {
      throw new CommandInputError(this);
    }

    String appId = programIdParts[0];
    String programName = programIdParts[1];
    String version = arguments.getOptional(ArgumentName.APP_VERSION.toString());
    String appVersion = version == null ? ApplicationId.DEFAULT_VERSION : version;
    ProgramId programId = cliConfig.getCurrentNamespace().app(appId, appVersion)
      .program(elementType.getProgramType(), programName);
    String status = programClient.getStatus(programId);
    output.println(status);
  }

  @Override
  public String getPattern() {
    return String.format("get %s status <%s> [version <%s>]", elementType.getShortName(), elementType.getArgumentName(),
                         ArgumentName.APP_VERSION);
  }

  @Override
  public String getDescription() {
    return String.format("Gets the status of %s", Fragment.of(Article.A, elementType.getName()));
  }
}
