/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.gateway.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.cloud.gateway.support.Configurable;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * A {@link BeanFactoryInitializationAotProcessor} responsible for registering reflection
 * hints for {@link Configurable} beans.
 *
 * @author Josh Long
 * @author Olga Maciaszek-Sharma
 * @since 4.0.0
 */
class ConfigurableHintsRegistrationProcessor implements BeanFactoryInitializationAotProcessor {

	private static final Log LOG = LogFactory.getLog(ConfigurableHintsRegistrationProcessor.class);

	private static final String ROOT_GATEWAY_PACKAGE_NAME = "org.springframework.cloud.gateway";

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		return (generationContext, beanFactoryInitializationCode) -> {
			ReflectionHints hints = generationContext.getRuntimeHints().reflection();
			getConfigurableTypes().forEach(clazz -> hints.registerType(TypeReference.of(clazz),
					hint -> hint.withMembers(MemberCategory.DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS,
							MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)));
		};
	}

	private static Set<Class<?>> getConfigurableTypes() {
		Set<Class<?>> classesToAdd = getClassesToAdd();
		Set<Class<?>> genericsToAdd = new HashSet<>();
		for (Class<?> clazz : classesToAdd) {
			ResolvableType resolvableType = ResolvableType.forType(clazz);
			if (resolvableType.getSuperType().hasGenerics()) {
				genericsToAdd.addAll(Arrays.stream(resolvableType.getSuperType().getGenerics())
						.map(ResolvableType::toClass).collect(Collectors.toSet()));
			}
		}
		classesToAdd.addAll(genericsToAdd);
		return classesToAdd.stream().filter(Objects::nonNull).collect(Collectors.toSet());

	}

	@SuppressWarnings({ "rawtypes" })
	private static Set<Class<?>> getClassesToAdd() {
		Set<Class<?>> classesToAdd = new HashSet<>();
		ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
		provider.addIncludeFilter(new AssignableTypeFilter(Configurable.class));
		Set<BeanDefinition> components = provider.findCandidateComponents(ROOT_GATEWAY_PACKAGE_NAME);
		for (BeanDefinition component : components) {
			Class clazz;
			try {
				clazz = Class.forName(component.getBeanClassName());
				classesToAdd.add(clazz);
			}
			catch (NoClassDefFoundError | ClassNotFoundException exception) {
				if (LOG.isWarnEnabled()) {
					LOG.warn(exception);
				}
			}
		}
		return classesToAdd;
	}

}
