package com.tsystemsmms.cmcc.cmccoperator.components;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Optional;

@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum ComponentState {
    NotApplicable,
    ResourceNeedsUpdate,
    WaitingForDeployment,
    WaitingForReadiness,
    WaitingForShutdown,
    WaitingForCompletion,
    Ready;

    public boolean isRelevant() {
        return this != NotApplicable;
    }

    public boolean isWaiting() {
        return switch (this) {
            case Ready, NotApplicable -> false;
            case ResourceNeedsUpdate,
                 WaitingForDeployment, WaitingForReadiness,
                 WaitingForShutdown, WaitingForCompletion
                    -> true;
        };
    }

    public Optional<Boolean> isReady() {
        return switch (this) {
            case NotApplicable -> Optional.empty();
            case ResourceNeedsUpdate,
                 WaitingForDeployment, WaitingForReadiness,
                 WaitingForShutdown, WaitingForCompletion
                    -> Optional.of(false);
            case Ready -> Optional.of(true);
        };
    }
}

