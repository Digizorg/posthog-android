package com.posthog.internal.surveys

import com.posthog.PostHogInternal
import com.posthog.surveys.Survey

@PostHogInternal
public interface PostHogSurveysHandler {
    /**
     * To be called by Posthog when an event is captured
     */
    public fun onEvent(
        event: String,
        properties: Map<String, Any>? = null,
    )

    /**
     * Notifies the integration that surveys have been loaded or updated from remote config.
     * This should be called by PostHog when remote config loads and surveys are parsed.
     *
     * @param surveys List of surveys loaded from remote config (may be empty)
     */
    public fun onSurveysLoaded(surveys: List<Survey>)

    /**
     * Returns all surveys matching the current user's display conditions:
     * active date range, device type, unseen status, wait period, feature flags,
     * and event-based activation. Used by the public PostHog.getActiveMatchingSurveys API
     * so that custom UIs get the same filtering behaviour as the built-in auto-render flow
     * (and as the iOS/JS SDKs).
     */
    public fun getActiveMatchingSurveys(): List<Survey>

    /** Marks a survey as locally completed so it is excluded immediately. */
    public fun markSurveySeen(
        surveyId: String,
        survey: Survey?,
    )

    /** Clears user-specific in-memory survey state after an identity reset. */
    public fun resetSurveyState()
}
