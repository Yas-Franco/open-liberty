---
name: Feature Serviceability Summary
about: Create a summary of the serviceability details for a feature
title: Feature Serviceability Summary
labels: Feature Serviceability Summary
assignees: ''

---

## Feature Serviceability Summary

Issue that covers the serviceability details of a feature for the @OpenLiberty/serviceability-approvers to review. When opened, please link this issue to the feature issue.

### UFO

1. Does the UFO identify the most likely problems customers will see and identify how the feature will enable them to diagnose and solve those problems without resorting to raising a PMR?
2. Have these issues been addressed in the implementation?

### Test and Demo

As part of the serviceability process we're asking feature teams to test and analyze common problem paths for serviceability and demo those problem paths to someone not involved in the development of the feature (eg. L2, test team, or another development team).

1. What problem paths were tested and demonstrated?
2. Who did you demo to?
3. Do the people you demo'd to agree that the serviceability of the demonstrated problem scenarios is sufficient to avoid PMRs for any problems customers are likely to encounter, or that L2 should be able to quickly address those problems without need to engage L3?

### Service

Which L2 / L3 queues will handle PMRs for this feature? Ensure they are present in the contact reference file and in the queue contact summary, and that the respective L2/L3 teams know they are supporting it. Ask the [Serviceability Approvers](https://github.com/orgs/OpenLiberty/teams/serviceability-approvers/members) if you need links or more info.

### Metrics

Does this feature add any new metrics or emit any new JSON events? If yes, have you updated the [JMX metrics reference list](https://openliberty.io/docs/latest/jmx-metrics-list.html) / [Metrics reference list](https://openliberty.io/docs/latest/metrics-list.html) / [JSON log events reference list](https://openliberty.io/docs/latest/json-log-events-list.html) in the Open Liberty docs?