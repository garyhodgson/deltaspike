/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.deltaspike.core.impl.config;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.BeforeShutdown;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;

import java.util.ArrayList;
import java.util.List;

import org.apache.deltaspike.core.api.config.ConfigResolver;
import org.apache.deltaspike.core.api.config.PropertyConfigSource;
import org.apache.deltaspike.core.spi.activation.Deactivatable;
import org.apache.deltaspike.core.spi.config.ConfigSource;
import org.apache.deltaspike.core.util.ClassDeactivationUtils;

/**
 * This extension handles {@link org.apache.deltaspike.core.api.config.PropertyConfigSource}s
 * provided by users.
 */
public class ConfigurationExtension implements Extension, Deactivatable
{
    private boolean isActivated = false;

    private List<Class<? extends PropertyConfigSource>> configSourcesClasses
        = new ArrayList<Class<?  extends PropertyConfigSource>>();


    @SuppressWarnings("UnusedDeclaration")
    protected void init(@Observes BeforeBeanDiscovery beforeBeanDiscovery)
    {
        isActivated = ClassDeactivationUtils.isActivated(getClass());
    }

    @SuppressWarnings("UnusedDeclaration")
    public void collectUserConfigSources(@Observes ProcessAnnotatedType<? extends PropertyConfigSource> pat)
    {
        if (!isActivated)
        {
            return;
        }

        Class<? extends PropertyConfigSource> pcsClass = pat.getAnnotatedType().getJavaClass();
        if (pcsClass.isAnnotation() ||
            pcsClass.isInterface()  ||
            pcsClass.isSynthetic()  ||
            pcsClass.isArray()      ||
            pcsClass.isEnum()         )
        {
            // we only like to add real classes
            return;
        }

        configSourcesClasses.add(pcsClass);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void registerUserConfigSources(@Observes AfterDeploymentValidation adv)
    {
        if (!isActivated)
        {
            return;
        }

        List<ConfigSource> configSources = new ArrayList<ConfigSource>();

        for (Class<? extends PropertyConfigSource> configSourcesClass : configSourcesClasses)
        {
            configSources.addAll(createPropertyConfigSource(configSourcesClass));
        }

        // finally add all
        ConfigResolver.addConfigSources(configSources);
    }

    /**
     * This method triggers freeing of the ConfigSources.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void freeConfigSources(@Observes BeforeShutdown bs)
    {
        ConfigResolver.freeConfigSources();
    }

    /**
     * @return create an instance of the given PropertyConfigSource and return all it's ConfigSources.
     */
    private List<ConfigSource> createPropertyConfigSource(Class<? extends PropertyConfigSource> configSourcesClass)
    {
        try
        {
            PropertyConfigSource propertyConfigSource = configSourcesClass.newInstance();
            EnvironmentPropertyConfigSourceProvider environmentPropertyConfigSourceProvider
                = new EnvironmentPropertyConfigSourceProvider(propertyConfigSource.getPropertyFileName());

            return environmentPropertyConfigSourceProvider.getConfigSources();
        }
        catch (InstantiationException e)
        {
            throw new RuntimeException("Cannot create user ConfigSource " + configSourcesClass.getName(), e);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException("Cannot create user ConfigSource " + configSourcesClass.getName(), e);
        }
    }

}
