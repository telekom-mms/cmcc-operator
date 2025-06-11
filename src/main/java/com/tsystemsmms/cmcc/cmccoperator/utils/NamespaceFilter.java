package com.tsystemsmms.cmcc.cmccoperator.utils;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

@Component
@NoArgsConstructor
public class NamespaceFilter<T extends HasMetadata> implements GenericFilter<T> {

    @Getter
    private static Set<String> namespaceIncludes = Collections.emptySet();
    @Getter
    private static Set<String> namespaceExcludes = Collections.emptySet();

    // use spring bean logic in order to collect spring properties into STATIC field
    // see https://www.baeldung.com/spring-inject-static-field
    // that allows using the bean AND the class for filtering (i.e. in Reconcilers filter annotations)
    @SuppressWarnings("squid:S2696")
    @Value("${cmcc.scope.namespace.include:}")
    public void setNamespaceIncludes(Set<String> namespaces) {
        NamespaceFilter.namespaceIncludes = namespaces;
    }

    @SuppressWarnings("squid:S2696")
    @Value("${cmcc.scope.namespace.exclude:}")
    public void setNamespaceExcludes(Set<String> namespaces) {
        NamespaceFilter.namespaceExcludes = namespaces;
    }

    public static boolean isScoped() {
        return !namespaceIncludes.isEmpty() || !namespaceExcludes.isEmpty();
    }

    public static String getLogMessage() {
        if (!isScoped()) {
            return "";
        }
        return "for namespaces: " + (namespaceIncludes.isEmpty() ? "[all namespaces]" : namespaceIncludes)
                + (namespaceExcludes.isEmpty() ? ", no excludes" : (", excludes: " + namespaceExcludes));
    }

    @Override
    public boolean accept(T o) {
        var namespace = o.getMetadata().getNamespace();
        return (namespaceIncludes.isEmpty() || namespaceIncludes.stream().anyMatch(namespace::equals))
                && namespaceExcludes.stream().noneMatch(namespace::equals);
    }
}
