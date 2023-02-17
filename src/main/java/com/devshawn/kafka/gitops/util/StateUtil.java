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
                .flatMap(Settings::topics)
                .flatMap(SettingsTopics::defaults)
                .flatMap(SettingsTopicsDefaults::replication);
    }

    public static boolean isDescribeTopicAclEnabled(DesiredStateFile desiredStateFile) {
        return desiredStateFile.getSettings()
                .flatMap(Settings::services)
                .flatMap(SettingsServices::acls)
                .flatMap(SettingsServicesAcls::describeTopicEnabled)
                .orElse(false);
    }
}
