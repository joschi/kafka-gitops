package com.devshawn.kafka.gitops.util;

import com.devshawn.kafka.gitops.domain.state.DesiredStateFile;
import com.devshawn.kafka.gitops.domain.state.settings.Settings;
import com.devshawn.kafka.gitops.domain.state.settings.SettingsServices;
import com.devshawn.kafka.gitops.domain.state.settings.SettingsServicesAcls;
import com.devshawn.kafka.gitops.domain.state.settings.SettingsTopics;
import com.devshawn.kafka.gitops.domain.state.settings.SettingsTopicsDefaults;

import java.util.Optional;

public class StateUtil {

    public static Optional<Integer> fetchReplication(DesiredStateFile desiredStateFile) {
        return desiredStateFile.getSettings()
                .flatMap(Settings::getTopics)
                .flatMap(SettingsTopics::getDefaults)
                .flatMap(SettingsTopicsDefaults::getReplication);
    }

    public static boolean isDescribeTopicAclEnabled(DesiredStateFile desiredStateFile) {
        return desiredStateFile.getSettings()
                .flatMap(Settings::getServices)
                .flatMap(SettingsServices::getAcls)
                .flatMap(SettingsServicesAcls::getDescribeTopicEnabled)
                .orElse(false);
    }
}
