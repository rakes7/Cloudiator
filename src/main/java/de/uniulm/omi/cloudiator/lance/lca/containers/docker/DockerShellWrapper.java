/*
 * Copyright (c) 2014-2015 University of Ulm
 *
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package de.uniulm.omi.cloudiator.lance.lca.containers.docker;

import de.uniulm.omi.cloudiator.lance.lifecycle.ExecutionResult;
import de.uniulm.omi.cloudiator.lance.lifecycle.Shell;

class DockerShellWrapper implements Shell {
	final DockerShell shell; 
	DockerShellWrapper(DockerShell _shell) { shell = _shell; }
	
	@Override
	public ExecutionResult executeCommand(String command) {
		ExecutionResult result = shell.executeCommand(command);
		checkResult(command, result);
		return result;
	}

	@Override
	public ExecutionResult executeBlockingCommand(String res) {
		ExecutionResult result = shell.executeBlockingCommand(res);
		checkResult(res, result);
		return result;
	}
	
	private void checkResult(String command, ExecutionResult result) {
		if(result.isSuccess()) return;
		System.err.println("unsuccessull command '" + command + "': " + result.toString());
	}
}
