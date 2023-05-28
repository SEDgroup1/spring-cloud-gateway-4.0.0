/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.filter.cors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.ApplicationListener;
import org.springframework.web.cors.CorsConfiguration;

/**
 * @author Fredrich Ombico
 * @author Abel Salgado Romero
 */
public class CorsGatewayFilterApplicationListener implements ApplicationListener<RefreshRoutesEvent> {

	private final GlobalCorsProperties globalCorsProperties;

	private final RoutePredicateHandlerMapping routePredicateHandlerMapping;

	private final RouteDefinitionLocator routeDefinitionLocator;

	private static final String PATH_PREDICATE_NAME = "Path";

	private static final String METADATA_KEY = "cors";

	private static final String ALL_PATHS = "/**";

	public CorsGatewayFilterApplicationListener(GlobalCorsProperties globalCorsProperties,
			RoutePredicateHandlerMapping routePredicateHandlerMapping, RouteDefinitionLocator routeDefinitionLocator) {
		this.globalCorsProperties = globalCorsProperties;
		this.routePredicateHandlerMapping = routePredicateHandlerMapping;
		this.routeDefinitionLocator = routeDefinitionLocator;
	}

	@Override
	public void onApplicationEvent(RefreshRoutesEvent event) {
		routeDefinitionLocator.getRouteDefinitions().collectList().subscribe(routeDefinitions -> {
			// pre-populate with pre-existing global cors configurations to combine with.
			var corsConfigurations = new HashMap<>(globalCorsProperties.getCorsConfigurations());

			routeDefinitions.forEach(routeDefinition -> {
				var corsConfiguration = getCorsConfiguration(routeDefinition);
				corsConfiguration.ifPresent(configuration -> {
					var pathPredicate = getPathPredicate(routeDefinition);
					corsConfigurations.put(pathPredicate, configuration);
				});
			});

			routePredicateHandlerMapping.setCorsConfigurations(corsConfigurations);
		});
	}

	private String getPathPredicate(RouteDefinition routeDefinition) {
		return routeDefinition.getPredicates().stream()
				.filter(predicate -> PATH_PREDICATE_NAME.equals(predicate.getName())).findFirst()
				.flatMap(predicate -> predicate.getArgs().values().stream().findFirst()).orElse(ALL_PATHS);
	}

	@SuppressWarnings("unchecked")
	private Optional<CorsConfiguration> getCorsConfiguration(RouteDefinition routeDefinition) {
		Map<String, Object> corsMetadata = (Map<String, Object>) routeDefinition.getMetadata().get(METADATA_KEY);
		if (corsMetadata != null) {
			final CorsConfiguration corsConfiguration = new CorsConfiguration();

			findValue(corsMetadata, "allowCredentials")
					.ifPresent(value -> corsConfiguration.setAllowCredentials((Boolean) value));
			findValue(corsMetadata, "allowedHeaders")
					.ifPresent(value -> corsConfiguration.setAllowedHeaders(asList(value)));
			findValue(corsMetadata, "allowedMethods")
					.ifPresent(value -> corsConfiguration.setAllowedMethods(asList(value)));
			findValue(corsMetadata, "allowedOriginPatterns")
					.ifPresent(value -> corsConfiguration.setAllowedOriginPatterns(asList(value)));
			findValue(corsMetadata, "allowedOrigins")
					.ifPresent(value -> corsConfiguration.setAllowedOrigins(asList(value)));
			findValue(corsMetadata, "exposedHeaders")
					.ifPresent(value -> corsConfiguration.setExposedHeaders(asList(value)));
			findValue(corsMetadata, "maxAge").ifPresent(value -> corsConfiguration.setMaxAge(asLong(value)));

			return Optional.of(corsConfiguration);
		}

		return Optional.empty();
	}

	private Optional<Object> findValue(Map<String, Object> metadata, String key) {
		Object value = metadata.get(key);
		return Optional.ofNullable(value);
	}

	private List<String> asList(Object value) {
		if (value instanceof String) {
			return Arrays.asList((String) value);
		}
		if (value instanceof Map) {
			return new ArrayList<>(((Map<?, String>) value).values());
		}
		else {
			return (List<String>) value;
		}
	}

	private Long asLong(Object value) {
		if (value instanceof Integer) {
			return ((Integer) value).longValue();
		}
		else {
			return (Long) value;
		}
	}

}
