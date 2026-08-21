package com.posthog.android.surveys

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.posthog.PostHogConfig
import com.posthog.PostHogInterface
import com.posthog.internal.PostHogMemoryPreferences
import com.posthog.internal.PostHogPreferences
import com.posthog.surveys.Survey
import com.posthog.surveys.SurveyConditions
import com.posthog.surveys.SurveyEventCondition
import com.posthog.surveys.SurveyEventConditions
import com.posthog.surveys.SurveyMatchType
import com.posthog.surveys.SurveyPropertyFilter
import com.posthog.surveys.SurveyType
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
internal class PostHogSurveysEventPropertyFilterTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun createIntegration(preferences: PostHogPreferences = PostHogMemoryPreferences()): PostHogSurveysIntegration {
        val config =
            PostHogConfig("test-api-key").apply {
                surveys = true
                cachePreferences = preferences
            }
        val integration = PostHogSurveysIntegration(context, config)
        val fake = mock<PostHogInterface>()
        whenever(fake.isFeatureEnabled(any(), any(), any())).thenReturn(true)
        integration.install(fake)
        return integration
    }

    private fun createSurvey(
        id: String,
        eventConditions: List<SurveyEventCondition>,
        repeatedActivation: Boolean = true,
    ): Survey {
        return Survey(
            id = id,
            name = "Test Survey $id",
            type = SurveyType.API,
            questions = emptyList(),
            description = null,
            featureFlagKeys = null,
            linkedFlagKey = null,
            targetingFlagKey = null,
            internalTargetingFlagKey = null,
            conditions =
                SurveyConditions(
                    url = null,
                    urlMatchType = null,
                    selector = null,
                    deviceTypes = null,
                    deviceTypesMatchType = null,
                    seenSurveyWaitPeriodInDays = null,
                    events =
                        SurveyEventConditions(
                            repeatedActivation = repeatedActivation,
                            values = eventConditions,
                        ),
                ),
            appearance = null,
            currentIteration = null,
            currentIterationStartDate = null,
            startDate = java.util.Date(),
            endDate = null,
            schedule = null,
        )
    }

    @Test
    fun `event without property filters activates survey on matching event name`() {
        val integration = createIntegration()
        val survey = createSurvey("s1", listOf(SurveyEventCondition(name = "purchase")), repeatedActivation = false)
        integration.onSurveysLoaded(listOf(survey))

        integration.onEvent("purchase", mapOf("amount" to 100))

        assertTrue(integration.getActiveMatchingSurveys().any { it.id == "s1" })
    }

    @Test
    fun `event activation survives integration restart until survey is seen`() {
        val preferences = PostHogMemoryPreferences()
        val survey = createSurvey("s1", listOf(SurveyEventCondition(name = "purchase")))
        val firstIntegration = createIntegration(preferences)
        firstIntegration.onSurveysLoaded(listOf(survey))
        firstIntegration.onEvent("purchase")

        val restoredIntegration = createIntegration(preferences)
        restoredIntegration.onSurveysLoaded(listOf(survey))
        assertTrue(restoredIntegration.getActiveMatchingSurveys().any { it.id == "s1" })

        restoredIntegration.markSurveySeen(survey.id, survey)
        assertTrue(restoredIntegration.getActiveMatchingSurveys().isEmpty())

        val completedIntegration = createIntegration(preferences)
        completedIntegration.onSurveysLoaded(listOf(survey))
        assertTrue(completedIntegration.getActiveMatchingSurveys().isEmpty())
    }

    @Test
    fun `event with exact property filter activates only when property matches`() {
        val integration = createIntegration()
        val survey =
            createSurvey(
                "s1",
                listOf(
                    SurveyEventCondition(
                        name = "purchase",
                        propertyFilters =
                            mapOf(
                                "product_type" to
                                    SurveyPropertyFilter(
                                        values = listOf("premium"),
                                        operator = SurveyMatchType.EXACT,
                                    ),
                            ),
                    ),
                ),
            )
        integration.onSurveysLoaded(listOf(survey))

        // Non-matching property
        integration.onEvent("purchase", mapOf("product_type" to "basic"))
        assertTrue(integration.getActiveMatchingSurveys().isEmpty())

        // Matching property
        integration.onEvent("purchase", mapOf("product_type" to "premium"))
        assertTrue(integration.getActiveMatchingSurveys().any { it.id == "s1" })
    }

    @Test
    fun `event with gt property filter activates only when value is greater`() {
        val integration = createIntegration()
        val survey =
            createSurvey(
                "s1",
                listOf(
                    SurveyEventCondition(
                        name = "purchase",
                        propertyFilters =
                            mapOf(
                                "amount" to
                                    SurveyPropertyFilter(
                                        values = listOf("100"),
                                        operator = SurveyMatchType.GT,
                                    ),
                            ),
                    ),
                ),
            )
        integration.onSurveysLoaded(listOf(survey))

        integration.onEvent("purchase", mapOf("amount" to 50))
        assertTrue(integration.getActiveMatchingSurveys().isEmpty())

        integration.onEvent("purchase", mapOf("amount" to 150))
        assertTrue(integration.getActiveMatchingSurveys().any { it.id == "s1" })
    }

    @Test
    fun `event with lt property filter activates only when value is less`() {
        val integration = createIntegration()
        val survey =
            createSurvey(
                "s1",
                listOf(
                    SurveyEventCondition(
                        name = "purchase",
                        propertyFilters =
                            mapOf(
                                "amount" to
                                    SurveyPropertyFilter(
                                        values = listOf("100"),
                                        operator = SurveyMatchType.LT,
                                    ),
                            ),
                    ),
                ),
            )
        integration.onSurveysLoaded(listOf(survey))

        integration.onEvent("purchase", mapOf("amount" to 150))
        assertTrue(integration.getActiveMatchingSurveys().isEmpty())

        integration.onEvent("purchase", mapOf("amount" to 50))
        assertTrue(integration.getActiveMatchingSurveys().any { it.id == "s1" })
    }

    @Test
    fun `event with multiple property filters requires all to match`() {
        val integration = createIntegration()
        val survey =
            createSurvey(
                "s1",
                listOf(
                    SurveyEventCondition(
                        name = "purchase",
                        propertyFilters =
                            mapOf(
                                "product_type" to
                                    SurveyPropertyFilter(
                                        values = listOf("premium"),
                                        operator = SurveyMatchType.EXACT,
                                    ),
                                "amount" to
                                    SurveyPropertyFilter(
                                        values = listOf("100"),
                                        operator = SurveyMatchType.GT,
                                    ),
                            ),
                    ),
                ),
            )
        integration.onSurveysLoaded(listOf(survey))

        // Only one matches
        integration.onEvent("purchase", mapOf("product_type" to "premium", "amount" to 50))
        assertTrue(integration.getActiveMatchingSurveys().isEmpty())

        // Both match
        integration.onEvent("purchase", mapOf("product_type" to "premium", "amount" to 200))
        assertTrue(integration.getActiveMatchingSurveys().any { it.id == "s1" })
    }

    @Test
    fun `missing event property does not activate survey`() {
        val integration = createIntegration()
        val survey =
            createSurvey(
                "s1",
                listOf(
                    SurveyEventCondition(
                        name = "purchase",
                        propertyFilters =
                            mapOf(
                                "product_type" to
                                    SurveyPropertyFilter(
                                        values = listOf("premium"),
                                        operator = SurveyMatchType.EXACT,
                                    ),
                            ),
                    ),
                ),
            )
        integration.onSurveysLoaded(listOf(survey))

        integration.onEvent("purchase", mapOf("other_prop" to "value"))
        assertTrue(integration.getActiveMatchingSurveys().isEmpty())

        integration.onEvent("purchase", null)
        assertTrue(integration.getActiveMatchingSurveys().isEmpty())
    }

    @Test
    fun `icontains property filter matches case-insensitively`() {
        val integration = createIntegration()
        val survey =
            createSurvey(
                "s1",
                listOf(
                    SurveyEventCondition(
                        name = "search",
                        propertyFilters =
                            mapOf(
                                "query" to
                                    SurveyPropertyFilter(
                                        values = listOf("product"),
                                        operator = SurveyMatchType.I_CONTAINS,
                                    ),
                            ),
                    ),
                ),
            )
        integration.onSurveysLoaded(listOf(survey))

        integration.onEvent("search", mapOf("query" to "New PRODUCT features"))
        assertTrue(integration.getActiveMatchingSurveys().any { it.id == "s1" })
    }

    @Test
    fun `is_not property filter excludes matching values`() {
        val integration = createIntegration()
        val survey =
            createSurvey(
                "s1",
                listOf(
                    SurveyEventCondition(
                        name = "purchase",
                        propertyFilters =
                            mapOf(
                                "status" to
                                    SurveyPropertyFilter(
                                        values = listOf("cancelled"),
                                        operator = SurveyMatchType.IS_NOT,
                                    ),
                            ),
                    ),
                ),
            )
        integration.onSurveysLoaded(listOf(survey))

        integration.onEvent("purchase", mapOf("status" to "cancelled"))
        assertTrue(integration.getActiveMatchingSurveys().isEmpty())

        integration.onEvent("purchase", mapOf("status" to "completed"))
        assertTrue(integration.getActiveMatchingSurveys().any { it.id == "s1" })
    }

    @Test
    fun `non-matching event name does not activate survey even with matching properties`() {
        val integration = createIntegration()
        val survey =
            createSurvey(
                "s1",
                listOf(
                    SurveyEventCondition(
                        name = "purchase",
                        propertyFilters =
                            mapOf(
                                "product_type" to
                                    SurveyPropertyFilter(
                                        values = listOf("premium"),
                                        operator = SurveyMatchType.EXACT,
                                    ),
                            ),
                    ),
                ),
            )
        integration.onSurveysLoaded(listOf(survey))

        integration.onEvent("page_view", mapOf("product_type" to "premium"))
        assertTrue(integration.getActiveMatchingSurveys().isEmpty())
    }
}
