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

import static de.uniulm.omi.cloudiator.lance.application.component.ComponentType.DOCKER;

import de.uniulm.omi.cloudiator.lance.application.DeploymentContext;
import de.uniulm.omi.cloudiator.lance.application.component.ComponentType;
import de.uniulm.omi.cloudiator.lance.application.component.DeployableComponent;
import de.uniulm.omi.cloudiator.lance.application.component.InPort;
import de.uniulm.omi.cloudiator.lance.container.spec.os.OperatingSystem;
import de.uniulm.omi.cloudiator.lance.container.standard.ContainerLogic;
import de.uniulm.omi.cloudiator.lance.lca.HostContext;
import de.uniulm.omi.cloudiator.lance.lca.StaticEnvVars;
import de.uniulm.omi.cloudiator.lance.lca.container.ComponentInstanceId;
import de.uniulm.omi.cloudiator.lance.lca.container.ContainerException;
import de.uniulm.omi.cloudiator.lance.lca.container.environment.BashExportBasedVisitor;
import de.uniulm.omi.cloudiator.lance.lca.container.environment.PropertyVisitor;
import de.uniulm.omi.cloudiator.lance.lca.container.port.DownstreamAddress;
import de.uniulm.omi.cloudiator.lance.lca.container.port.InportAccessor;
import de.uniulm.omi.cloudiator.lance.lca.container.port.NetworkHandler;
import de.uniulm.omi.cloudiator.lance.lca.container.port.PortDiff;
import de.uniulm.omi.cloudiator.lance.lca.container.port.PortRegistryTranslator;
import de.uniulm.omi.cloudiator.lance.lca.containers.docker.connector.DockerConnector;
import de.uniulm.omi.cloudiator.lance.lca.containers.docker.connector.DockerException;
import de.uniulm.omi.cloudiator.lance.lifecycle.HandlerType;
import de.uniulm.omi.cloudiator.lance.lifecycle.LifecycleActionInterceptor;
import de.uniulm.omi.cloudiator.lance.lifecycle.LifecycleHandlerType;
import de.uniulm.omi.cloudiator.lance.lifecycle.LifecycleStore;
import de.uniulm.omi.cloudiator.lance.lifecycle.detector.DetectorType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//todo: install/close shell after each "atomic" action (e.g. also in each lifecycle transition or pre/postprocessDetector)
//sofar:  install shell in doInit, close shell in completeInit
//        install shell in preDestroy, close shell in completeShutDown
//        install shell in preprocessDetector, close shell in postProcessDetector
//        install shell in preprocessPortUpdate, close shell in postprocessPortUpdate
public class DockerContainerLogic implements ContainerLogic, LifecycleActionInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(DockerContainerManager.class);
        
  private final ComponentInstanceId myId;
  private final DockerConnector client;

  private final DockerShellFactory shellFactory;
  private final DeploymentContext deploymentContext;

  private final DockerImageHandler imageHandler;
  private final NetworkHandler networkHandler;

  private final DeployableComponent myComponent;
  private final HostContext hostContext;

  private final StaticEnvVars instVars;
  private final StaticEnvVars hostVars;

  private final Map<String, String> envVarsStatic;
  private final Map<String, String> envVarsDynamic;

  //todo: not needed in post-colosseum version, as the environment-var names should be set correctly then
  private static final Map<String, String> translateMap;
  static {
    Map<String, String> tmpMap = new HashMap<>();
    tmpMap.put("host.vm.id", "VM_ID");
    tmpMap.put("INSTANCE_ID", "INSTANCE_ID");
    translateMap = Collections.unmodifiableMap(tmpMap);
  }

  DockerContainerLogic(ComponentInstanceId id, DockerConnector client, DeployableComponent comp,
    DeploymentContext ctx, OperatingSystem os, NetworkHandler network,
    DockerShellFactory shellFactoryParam, DockerConfiguration dockerConfig,
    HostContext hostContext) {
    this(id, client, os, ctx, comp, network, shellFactoryParam, dockerConfig, hostContext);
  }

  private DockerContainerLogic(ComponentInstanceId id, DockerConnector clientParam,
    OperatingSystem osParam,
    DeploymentContext ctx, DeployableComponent componentParam,
    NetworkHandler networkParam, DockerShellFactory shellFactoryParam,
    DockerConfiguration dockerConfigParam, HostContext hostContext) {

    if (osParam == null) {
      throw new NullPointerException("operating system has to be set.");
    }

    myId = id;
    instVars = this.myId;
    client = clientParam;
    imageHandler = new DockerImageHandler(osParam, new DockerOperatingSystemTranslator(),
    clientParam, componentParam, dockerConfigParam);
    deploymentContext = ctx;
    shellFactory = shellFactoryParam;
    myComponent = componentParam;
    networkHandler = networkParam;
    this.hostContext = hostContext;
    hostVars = this.hostContext;
    envVarsStatic = new HashMap<String, String>(instVars.getEnvVars());

    for(Map.Entry<String, String> kv : hostVars.getEnvVars().entrySet()) {
      envVarsStatic.put(kv.getKey(),kv.getValue());
    }

    envVarsDynamic = new HashMap<>();
    //todo: fill dynamic map with appropriate env-vars
  }

	@Override
	public ComponentInstanceId getComponentInstanceId() {
		return myId;
	}
        
  @Override
  public synchronized void doCreate() throws ContainerException {
    try {
      ComponentType type = myComponent.getType();
      if (type == DOCKER) {
        String imageName = myComponent.getName();
        executeCreation(imageName);
      }
      else
        executeCreation();
    } catch(DockerException de) {
        throw new ContainerException("docker problems. cannot create container " + myId, de);
    }
  }

  @Override
  public void preDestroy() throws ContainerException {
    if(!setStaticEnvironment(false)) {
      throw new ContainerException("cannot create shell for setting the environment vars in preDestroy");
    }
  }

  @Override
  public InportAccessor getPortMapper() {
    return ( (portName, clientState) -> {
      try {
        Integer portNumber = (Integer) deploymentContext.getProperty(portName, InPort.class);
        int mapped = client.getPortMapping(myId, portNumber);
        Integer i = Integer.valueOf(mapped);
        clientState.registerValueAtLevel(PortRegistryTranslator.PORT_HIERARCHY_0, i);
        clientState.registerValueAtLevel(PortRegistryTranslator.PORT_HIERARCHY_1, i);
        clientState.registerValueAtLevel(PortRegistryTranslator.PORT_HIERARCHY_2, portNumber);
      } catch(DockerException de) {
        throw new ContainerException("coulnd not register all port mappings", de);
      }
    });
  }

  @Override
  public boolean setStaticEnvironment(boolean useExistingShell) {
    DockerShell shell;

    if(!useExistingShell) {
      try {
        shell = client.getSideShell(myId);
        shellFactory.installDockerShell(shell);
      } catch(DockerException de) {
        //hack...
        return false;
      }
    } else {
      DockerShellWrapper w = shellFactory.createShell();
      shell = w.shell;
    }

    BashExportBasedVisitor visitor = new BashExportBasedVisitor(shell);
    visitor.visit("TERM", "DUMB");

    for(Entry<String, String> entry: envVarsStatic.entrySet()) {

      //todo: not needed in post-colosseum version, as the environment-var names should be set correctly then
      if(!translateMap.containsKey(entry.getKey()))
        continue;

      visitor.visit(translateMap.get(entry.getKey()), entry.getValue());
    }

    return true;
  }

  @Override
  public boolean setDynamicEnvironment(boolean useExistingShell) {
    //todo: implement
    return true;
  }

  @Override
  public String getLocalAddress() {
    try {
      return client.getContainerIp(myId);
    } catch(DockerException de) {
      // this means that that the container is not
      // up and running; hence, no IP address is
      // available. it is up to the caller to figure
      // out the semantics of this state
    }
    return null;
  }

	@Override
	public void completeInit() throws ContainerException {
		shellFactory.closeShell();
	}

  @Override
  public void completeShutDown() throws ContainerException {
    shellFactory.closeShell();
  }

  @Override
  public void prepare(HandlerType type) throws ContainerException {
    //if following method calls in this function call prepareEnvironment (e.g. preInstallAction)
    //the setStaticEnvironment is called twice
    if(!setStaticEnvironment(true)) {
      throw new ContainerException("cannot create shell for " + type + " in prepare method ");
    }
    if (type == LifecycleHandlerType.INSTALL) {
      preInstallAction();
    }
  }

  @Override
  public void doInit(LifecycleStore store) throws ContainerException {
    try {
      DockerShell shell = doStartContainer();
      shellFactory.installDockerShell(shell);
      if(!setStaticEnvironment(true)) {
        throw new ContainerException("cannot create shell for setting the environment vars in doInit");
      }
    } catch (ContainerException ce) {
      throw ce;
    } catch (Exception ex) {
      throw new ContainerException(ex);
    }
  }

  @Override
  public void doDestroy(boolean force) throws ContainerException {
    /* currently docker ignores the flag */
    if(!setStaticEnvironment(true)) {
      throw new ContainerException("cannot create shell for setting the environment vars in doDestroy");
    }
    try {
      client.stopContainer(myId);
    } catch (DockerException de) {
      throw new ContainerException(de);
    }
  }

  @Override
  public void preprocessPortUpdate(PortDiff<DownstreamAddress> diffSet) throws ContainerException {
    try {
      DockerShell shell = client.getSideShell(myId);
      shellFactory.installDockerShell(shell);
      if(!prepareEnvironment(shell, diffSet)) {
        throw new DockerException();
      }
    } catch (DockerException de) {
      throw new ContainerException("cannot create shell for port updates.", de);
    }
  }

  @Override
  public void postprocessPortUpdate(PortDiff<DownstreamAddress> diffSet) {
    shellFactory.closeShell();
  }

  //todo: make this method throwable with ContainerException
  @Override
  public void postprocess(HandlerType type) throws ContainerException {
    if(!setStaticEnvironment(true)) {
      throw new ContainerException("cannot create shell for " + type + " in prepare method ");
    }
    if (type == LifecycleHandlerType.PRE_INSTALL) {
      postPreInstall();
    } else if (type == LifecycleHandlerType.POST_INSTALL) {
      // TODO: do we have to make a snapshot after this? //
    }
  }

  @Override
  public void preprocessDetector(DetectorType type) throws ContainerException {
    // nothing special to do; just create a shell and prepare an environment //
    try {
      //DockerShell shell = client.getSideShell(myId);
      //shellFactory.installDockerShell(shell);
      DockerShellWrapper w = shellFactory.createShell();
      if(!prepareEnvironment(w.shell)) {
        throw new DockerException();
      }
    } catch (DockerException de) {
      throw new ContainerException("cannot create shell for " + type + " detector.", de);
    }
  }

  @Override
  public void postprocessDetector(DetectorType type) {
    // nothing special to do; just create a shell //
    // shellFactory.closeShell();
  }

  private DockerShell doStartContainer() throws ContainerException {
    final DockerShell dshell;
    try {
      dshell = client.startContainer(myId);
    } catch (DockerException de) {
      throw new ContainerException("cannot start container: " + myId, de);
    }
    return dshell;
  }

  private void preInstallAction() throws ContainerException {
    DockerShellWrapper w = shellFactory.createShell();
    prepareEnvironment(w.shell);
  }

  private void postPreInstall() {
    try {
      DockerShellWrapper w = shellFactory.createShell();
      BashExportBasedVisitor visitor = new BashExportBasedVisitor(w.shell);
      visitor.visit("TERM", "DUMB");
      imageHandler.runPostInstallAction(myId);
    } catch (DockerException de) {
      LOGGER.warn("could not update finalise image handling.", de);
    }
  }

  private boolean prepareEnvironment(DockerShell dshell) {
    return prepareEnvironment(dshell, null);
  }

  private boolean prepareEnvironment(DockerShell dshell, PortDiff<DownstreamAddress> diff) {
    BashExportBasedVisitor visitor = new BashExportBasedVisitor(dshell);

    if(!setStaticEnvironment(true)) {
      return false;
    }

    networkHandler.accept(visitor, diff);
    myComponent.accept(deploymentContext, visitor);

    return true;
  }

  private void executeCreation() throws DockerException {
    String target = imageHandler.doPullImages(myId);
    Map<Integer, Integer> portsToSet = networkHandler.findPortsToSet(deploymentContext);
    //@SuppressWarnings("unused") String dockerId =
    client.createContainer(target, myId, portsToSet);
  }

  private void executeCreation(String imageName) throws DockerException {
    String target = imageHandler.doPullImages(myId, imageName);
    Map<Integer, Integer> portsToSet = networkHandler.findPortsToSet(deploymentContext);
    //@SuppressWarnings("unused") String dockerId =
    client.createContainer(target, myId, portsToSet);
  }
}
