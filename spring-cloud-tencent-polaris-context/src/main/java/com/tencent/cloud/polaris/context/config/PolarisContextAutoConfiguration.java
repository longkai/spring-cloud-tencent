/*
 * Tencent is pleased to support the open source community by making Spring Cloud Tencent available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.tencent.cloud.polaris.context.config;

import java.util.List;

import com.tencent.cloud.polaris.context.ConditionalOnPolarisEnabled;
import com.tencent.cloud.polaris.context.ModifyAddress;
import com.tencent.cloud.polaris.context.PolarisConfigModifier;
import com.tencent.cloud.polaris.context.ServiceRuleManager;
import com.tencent.polaris.api.core.ConsumerAPI;
import com.tencent.polaris.api.core.ProviderAPI;
import com.tencent.polaris.api.exception.PolarisException;
import com.tencent.polaris.client.api.SDKContext;
import com.tencent.polaris.factory.api.DiscoveryAPIFactory;
import com.tencent.polaris.factory.api.RouterAPIFactory;
import com.tencent.polaris.router.api.core.RouterAPI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Autoconfiguration for Polaris {@link SDKContext}.
 *
 * @author Haotian Zhang
 */
@ConditionalOnPolarisEnabled
@EnableConfigurationProperties({PolarisContextProperties.class})
public class PolarisContextAutoConfiguration {

	@Bean(name = "polarisContext", initMethod = "init", destroyMethod = "destroy")
	@ConditionalOnMissingBean
	public SDKContext polarisContext(PolarisContextProperties properties, Environment environment, List<PolarisConfigModifier> modifierList) throws PolarisException {
		return SDKContext.initContextByConfig(properties.configuration(modifierList, () -> {
			return environment.getProperty("spring.cloud.client.ip-address");
		}));
	}

	@Bean
	@ConditionalOnMissingBean
	public ProviderAPI polarisProvider(SDKContext polarisContext) throws PolarisException {
		return DiscoveryAPIFactory.createProviderAPIByContext(polarisContext);
	}

	@Bean
	@ConditionalOnMissingBean
	public ConsumerAPI polarisConsumer(SDKContext polarisContext) throws PolarisException {
		return DiscoveryAPIFactory.createConsumerAPIByContext(polarisContext);
	}

	@Bean
	public RouterAPI polarisRouter(SDKContext polarisContext) throws PolarisException {
		return RouterAPIFactory.createRouterAPIByContext(polarisContext);
	}

	@Bean
	@ConditionalOnMissingBean
	public ModifyAddress polarisConfigModifier(PolarisContextProperties properties) {
		return new ModifyAddress(properties);
	}

	@Bean
	public ServiceRuleManager serviceRuleManager(SDKContext sdkContext, ConsumerAPI consumerAPI) {
		return new ServiceRuleManager(sdkContext, consumerAPI);
	}
}
