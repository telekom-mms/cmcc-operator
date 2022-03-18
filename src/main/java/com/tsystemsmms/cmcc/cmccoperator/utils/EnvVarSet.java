/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.utils;

import io.fabric8.kubernetes.api.model.EnvVar;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implements a set of EnvVars, with the name of the variable being the key to the set.
 * <p>
 * The set will ensure that no two env vars can have the same name. Adding an env var with the name of an existing one
 * will overwrite the existing entry.
 */
public class EnvVarSet implements Set<EnvVar> {
    private final HashMap<String, EnvVar> envVars = new HashMap<>();

    /**
     * Create a new empty EnvVarSet.
     */
    public EnvVarSet() {
        super();
    }

    /**
     * Create a new EnvVarSet initialized from the given set of vars.
     *
     * @param vars initial variables
     */
    public EnvVarSet(Collection<? extends EnvVar> vars) {
        super();
        addAll(vars);
    }

    /**
     * Returns the elements of the set as a list sorted by name.
     *
     * @return elements sorted by name
     */
    public List<EnvVar> toList() {
        TreeSet<String> keys = new TreeSet<>(envVars.keySet());
        LinkedList<EnvVar> sorted = new LinkedList<>();

        for (String key : keys) {
            sorted.add(envVars.get(key));
        }
        return sorted;
    }

    @Override
    public Iterator<EnvVar> iterator() {
        return envVars.values().iterator();
    }

    @Override
    public int size() {
        return envVars.size();
    }

    @Override
    public boolean isEmpty() {
        return envVars.isEmpty();
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    @Override
    public boolean contains(Object o) {
        return envVars.containsValue(o);
    }

    public boolean contains(String o) {
        return envVars.containsKey(o);
    }

    @Override
    public boolean add(EnvVar envVar) {
        // if the element to be added is not in the set, or the element in the set differs
        boolean changed = !envVars.containsKey(envVar.getName()) || !envVars.get(envVar.getName()).equals(envVar);

        envVars.put(envVar.getName(), envVar);
        return changed;
    }

    @Override
    public boolean remove(Object o) {
        if (o instanceof EnvVar) {
            EnvVar v = (EnvVar) o;
            return envVars.remove(v.getName()) != null;
        }
        return false;
    }

    @Override
    public void clear() {
        envVars.clear();
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public Object clone() {
        EnvVarSet n = new EnvVarSet();
        n.envVars.putAll(this.envVars);
        return n;
    }

    @Override
    public Spliterator<EnvVar> spliterator() {
        return envVars.values().spliterator();
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return envVars.equals(o);
    }

    @Override
    public int hashCode() {
        return envVars.hashCode();
    }

    @Override
    public boolean removeAll(Collection<?> vars) {
        if (vars == null)
            return false;
        return vars.stream().map(o -> {
            if (o instanceof EnvVar) {
                EnvVar v = (EnvVar) o;
                return envVars.remove(v.getName()) != null;
            }
            return false;
        }).filter((b) -> b).collect(Collectors.toUnmodifiableSet()).size() > 0;
    }

    public boolean removeAllKeys(Collection<String> keys) {
        keys.forEach(envVars::remove);
        return true;
    }

    @Override
    public Object[] toArray() {
        return envVars.values().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return envVars.values().toArray(a);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return envVars.values().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends EnvVar> c) {
        boolean changed = false;

        if (c == null)
            return false;

        for (EnvVar v : c) {
            if (add(v))
                changed = true;
        }
        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        Set<String> keys = new HashSet<>();

        for (Object o : c) {
            if (o instanceof EnvVar) {
                EnvVar v = (EnvVar) o;
                keys.add(v.getName());
            }
        }
        return envVars.keySet().retainAll(keys);
    }

    @Override
    public String toString() {
        return envVars.toString();
    }

    @Override
    public <T> T[] toArray(IntFunction<T[]> generator) {
        return envVars.values().toArray(generator);
    }

    @Override
    public boolean removeIf(Predicate<? super EnvVar> filter) {
        return envVars.values().removeIf(filter);
    }

    @Override
    public Stream<EnvVar> stream() {
        return envVars.values().stream();
    }

    @Override
    public Stream<EnvVar> parallelStream() {
        return envVars.values().parallelStream();
    }

    @Override
    public void forEach(Consumer<? super EnvVar> action) {
        envVars.values().forEach(action);
    }

    /**
     * Returns the variable with that name.
     *
     * @param key
     * @return
     */
    public Optional<EnvVar> get(String key) {
        return Optional.ofNullable(envVars.get(key));
    }

    public static EnvVarSet of() {
        return new EnvVarSet();
    }

    public static EnvVarSet of(EnvVar e1) {
        return new EnvVarSet(Set.of(e1));
    }

    public static EnvVarSet of(EnvVar e1, EnvVar e2) {
        return new EnvVarSet(Set.of(e1, e2));
    }

    public static EnvVarSet of(EnvVar e1, EnvVar e2, EnvVar e3) {
        return new EnvVarSet(Set.of(e1, e2, e3));
    }

    public static EnvVarSet of(EnvVar e1, EnvVar e2, EnvVar e3, EnvVar e4) {
        return new EnvVarSet(Set.of(e1, e2, e3, e4));
    }

    public static EnvVarSet of(EnvVar e1, EnvVar e2, EnvVar e3, EnvVar e4, EnvVar e5) {
        return new EnvVarSet(Set.of(e1, e2, e3, e4, e5));
    }

    public static EnvVarSet of(EnvVar e1, EnvVar e2, EnvVar e3, EnvVar e4, EnvVar e5, EnvVar e6) {
        return new EnvVarSet(Set.of(e1, e2, e3, e4, e5, e6));
    }

    public static EnvVarSet of(EnvVar e1, EnvVar e2, EnvVar e3, EnvVar e4, EnvVar e5, EnvVar e6, EnvVar e7) {
        return new EnvVarSet(Set.of(e1, e2, e3, e4, e5, e6, e7));
    }

    public static EnvVarSet of(EnvVar e1, EnvVar e2, EnvVar e3, EnvVar e4, EnvVar e5, EnvVar e6, EnvVar e7, EnvVar e8) {
        return new EnvVarSet(Set.of(e1, e2, e3, e4, e5, e6, e7, e8));
    }

    public static EnvVarSet of(EnvVar e1, EnvVar e2, EnvVar e3, EnvVar e4, EnvVar e5, EnvVar e6, EnvVar e7, EnvVar e8, EnvVar e9) {
        return new EnvVarSet(Set.of(e1, e2, e3, e4, e5, e6, e7, e8, e9));
    }

    public static EnvVarSet of(EnvVar e1, EnvVar e2, EnvVar e3, EnvVar e4, EnvVar e5, EnvVar e6, EnvVar e7, EnvVar e8, EnvVar e9, EnvVar e10) {
        return new EnvVarSet(Set.of(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10));
    }

    public static EnvVarSet of(EnvVar... elements) {
        return new EnvVarSet(Set.of(elements));
    }

    public static EnvVarSet copyOf(Collection<? extends EnvVar> coll) {
        return new EnvVarSet(coll);
    }
}
