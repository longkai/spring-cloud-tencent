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

package com.tencent.cloud.polaris.ratelimit.filter;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.tencent.cloud.common.metadata.MetadataContext;
import com.tencent.cloud.polaris.ratelimit.config.PolarisRateLimitProperties;
import com.tencent.cloud.polaris.ratelimit.constant.RateLimitConstant;
import com.tencent.cloud.polaris.ratelimit.resolver.RateLimitRuleArgumentServletResolver;
import com.tencent.cloud.polaris.ratelimit.spi.PolarisRateLimiterLimitedFallback;
import com.tencent.cloud.polaris.ratelimit.utils.QuotaCheckUtils;
import com.tencent.cloud.polaris.ratelimit.utils.RateLimitUtils;
import com.tencent.polaris.ratelimit.api.core.LimitAPI;
import com.tencent.polaris.ratelimit.api.rpc.Argument;
import com.tencent.polaris.ratelimit.api.rpc.QuotaResponse;
import com.tencent.polaris.ratelimit.api.rpc.QuotaResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter to check quota.
 *
 * @author Haotian Zhang, lepdou, cheese8
 */
@Order(RateLimitConstant.FILTER_ORDER)
public class QuotaCheckServletFilter extends OncePerRequestFilter {

	/**
	 * Default Filter Registration Bean Name Defined .
	 */
	public static final String QUOTA_FILTER_BEAN_NAME = "quotaFilterRegistrationBean";
	private static final Logger LOG = LoggerFactory.getLogger(QuotaCheckServletFilter.class);
	private final LimitAPI limitAPI;

	private final PolarisRateLimitProperties polarisRateLimitProperties;

	private final RateLimitRuleArgumentServletResolver rateLimitRuleArgumentResolver;

	private final PolarisRateLimiterLimitedFallback polarisRateLimiterLimitedFallback;

	private String rejectTips;

	public QuotaCheckServletFilter(LimitAPI limitAPI,
			PolarisRateLimitProperties polarisRateLimitProperties,
			RateLimitRuleArgumentServletResolver rateLimitRuleArgumentResolver,
			@Nullable PolarisRateLimiterLimitedFallback polarisRateLimiterLimitedFallback) {
		this.limitAPI = limitAPI;
		this.polarisRateLimitProperties = polarisRateLimitProperties;
		this.rateLimitRuleArgumentResolver = rateLimitRuleArgumentResolver;
		this.polarisRateLimiterLimitedFallback = polarisRateLimiterLimitedFallback;
	}

	@PostConstruct
	public void init() {
		rejectTips = RateLimitUtils.getRejectTips(polarisRateLimitProperties);
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain)
			throws ServletException, IOException {
		String localNamespace = MetadataContext.LOCAL_NAMESPACE;
		String localService = MetadataContext.LOCAL_SERVICE;

		Set<Argument> arguments = rateLimitRuleArgumentResolver.getArguments(request, localNamespace, localService);

		try {
			QuotaResponse quotaResponse = QuotaCheckUtils.getQuota(limitAPI,
					localNamespace, localService, 1, arguments, request.getRequestURI());

			if (quotaResponse.getCode() == QuotaResultCode.QuotaResultLimited) {
				if (!Objects.isNull(polarisRateLimiterLimitedFallback)) {
					response.setStatus(polarisRateLimiterLimitedFallback.rejectHttpCode());
					String contentType = new MediaType(polarisRateLimiterLimitedFallback.mediaType(), polarisRateLimiterLimitedFallback.charset()).toString();
					response.setContentType(contentType);
					response.getWriter().write(polarisRateLimiterLimitedFallback.rejectTips());
				}
				else {
					response.setStatus(polarisRateLimitProperties.getRejectHttpCode());
					response.setContentType("text/html;charset=UTF-8");
					response.getWriter().write(rejectTips);
				}
				return;
			}
			// Unirate
			if (quotaResponse.getCode() == QuotaResultCode.QuotaResultOk && quotaResponse.getWaitMs() > 0) {
				LOG.debug("The request of [{}] will waiting for {}ms.", request.getRequestURI(), quotaResponse.getWaitMs());
				Thread.sleep(quotaResponse.getWaitMs());
			}

		}
		catch (Throwable t) {
			// An exception occurs in the rate limiting API call,
			// which should not affect the call of the business process.
			LOG.error("fail to invoke getQuota, service is " + localService, t);
		}

		filterChain.doFilter(request, response);
	}

}
