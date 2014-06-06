package com.continuuity.data2.dataset2;

import com.continuuity.internal.data.dataset.Dataset;
import com.continuuity.internal.data.dataset.DatasetAdmin;
import com.continuuity.internal.data.dataset.DatasetDefinition;
import com.continuuity.internal.data.dataset.DatasetInstanceProperties;
import com.continuuity.internal.data.dataset.DatasetInstanceSpec;
import com.continuuity.internal.data.dataset.module.DatasetDefinitionRegistry;
import com.continuuity.internal.data.dataset.module.DatasetModule;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * A simple implementation of {@link com.continuuity.data2.dataset2.DatasetFramework} that keeps its state in
 * memory
 */
public class InMemoryDatasetFramework implements DatasetFramework {
  private final Set<String> modules = Sets.newHashSet();
  private final DatasetDefinitionRegistry registry;
  private final Map<String, DatasetInstanceSpec> instances = Maps.newHashMap();

  public InMemoryDatasetFramework() {
    this(new InMemoryDatasetDefinitionRegistry());
  }

  @Inject
  public InMemoryDatasetFramework(DatasetDefinitionRegistry registry) {
    this.registry = registry;
  }

  @Override
  public synchronized void register(String moduleName, Class<? extends DatasetModule> moduleClass)
    throws ModuleConflictException {

    if (modules.contains(moduleName)) {
      throw new ModuleConflictException("Cannot add module " + moduleName + ": it already exists.");
    }
    try {
      DatasetModule module = moduleClass.newInstance();
      module.register(registry);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  public void deleteModule(String moduleName) throws ModuleConflictException {
    modules.remove(moduleName);
    // todo: remove from registry & check for conflicts. It is fine for now, as we don't use delete with in-mem version
  }

  @Override
  public synchronized void addInstance(String datasetType, String datasetInstanceName, DatasetInstanceProperties props)
    throws InstanceConflictException, IOException {
    if (instances.get(datasetInstanceName) != null) {
      throw new InstanceConflictException("Dataset instance with name already exists: " + datasetInstanceName);
    }

    DatasetDefinition def = registry.get(datasetType);
    DatasetInstanceSpec spec = def.configure(datasetInstanceName, props);
    instances.put(datasetInstanceName, spec);
    def.getAdmin(spec).create();
  }

  @Override
  public Collection<String> getInstances() {
    return Collections.unmodifiableSet(instances.keySet());
  }

  @Override
  public boolean hasInstance(String instanceName) throws DatasetManagementException {
    return instances.containsKey(instanceName);
  }

  @Override
  public void deleteInstance(String datasetInstanceName) throws InstanceConflictException, IOException {
    DatasetInstanceSpec spec = instances.remove(datasetInstanceName);
    DatasetDefinition def = registry.get(spec.getType());
    def.getAdmin(spec).create();
  }

  @Override
  public synchronized <T extends DatasetAdmin> T getAdmin(String datasetInstanceName, ClassLoader classLoader)
    throws IOException {

    DatasetInstanceSpec spec = instances.get(datasetInstanceName);
    if (spec == null) {
      return null;
    }
    DatasetDefinition impl = registry.get(spec.getType());
    return (T) impl.getAdmin(spec);
  }

  @Override
  public synchronized <T extends Dataset> T getDataset(String datasetInstanceName, ClassLoader ignored)
    throws IOException {

    DatasetInstanceSpec spec = instances.get(datasetInstanceName);
    if (spec == null) {
      return null;
    }
    DatasetDefinition impl = registry.get(spec.getType());
    return (T) impl.getDataset(spec);
  }
}
