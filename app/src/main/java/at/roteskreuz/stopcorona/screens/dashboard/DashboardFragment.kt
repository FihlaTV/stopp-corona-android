package at.roteskreuz.stopcorona.screens.dashboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.Toolbar
import at.roteskreuz.stopcorona.R
import at.roteskreuz.stopcorona.constants.Constants
import at.roteskreuz.stopcorona.model.entities.infection.message.MessageType
import at.roteskreuz.stopcorona.model.exceptions.handleBaseCoronaErrors
import at.roteskreuz.stopcorona.screens.dashboard.CombinedExposureNotificationsState.EnabledWithError
import at.roteskreuz.stopcorona.screens.dashboard.CombinedExposureNotificationsState.EnabledWithError.Prerequisites
import at.roteskreuz.stopcorona.screens.dashboard.dialog.AutomaticHandshakeExplanationDialog
import at.roteskreuz.stopcorona.screens.dashboard.dialog.GooglePlayServicesNotAvailableDialog
import at.roteskreuz.stopcorona.screens.infection_info.startInfectionInfoFragment
import at.roteskreuz.stopcorona.screens.menu.startMenuFragment
import at.roteskreuz.stopcorona.screens.questionnaire.guideline.startQuestionnaireGuidelineFragment
import at.roteskreuz.stopcorona.screens.questionnaire.selfmonitoring.startQuestionnaireSelfMonitoringWithSubmissionDataFragment
import at.roteskreuz.stopcorona.screens.questionnaire.startQuestionnaireFragment
import at.roteskreuz.stopcorona.screens.reporting.reportStatus.guideline.startCertificateReportGuidelinesFragment
import at.roteskreuz.stopcorona.screens.reporting.startReportingActivity
import at.roteskreuz.stopcorona.skeleton.core.screens.base.fragment.BaseFragment
import at.roteskreuz.stopcorona.skeleton.core.utils.dipif
import at.roteskreuz.stopcorona.skeleton.core.utils.observeOnMainThread
import at.roteskreuz.stopcorona.utils.shareApp
import at.roteskreuz.stopcorona.utils.view.AccurateScrollListener
import at.roteskreuz.stopcorona.utils.view.LinearLayoutManagerAccurateOffset
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatusCodes
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.fragment_dashboard.*
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

/**
 * Sample dashboard.
 */
class DashboardFragment : BaseFragment(R.layout.fragment_dashboard) {

    companion object {
        private const val REQUEST_CODE_START_EXPOSURE_NOTIFICATION = Constants.Request.REQUEST_DASHBOARD + 1
    }

    override val isToolbarVisible: Boolean = true

    override fun getTitle(): String? {
        return "" // blank
    }

    private val viewModel: DashboardViewModel by viewModel()

    private val controller: DashboardController by lazy {
        DashboardController(
            context = requireContext(),
            onAutomaticHandshakeInformationClick = {
                AutomaticHandshakeExplanationDialog().show()
            },
            onFeelingClick = {
                startQuestionnaireFragment()
            },
            onReportClick = {
                startReportingActivity(MessageType.InfectionLevel.Red)
            },
            onHealthStatusClick = { healthStatusData ->
                when (healthStatusData) {
                    is HealthStatusData.SicknessCertificate -> {
                        startCertificateReportGuidelinesFragment()
                    }
                    HealthStatusData.SelfTestingSymptomsMonitoring -> {
                        startQuestionnaireSelfMonitoringWithSubmissionDataFragment()
                    }
                    is HealthStatusData.SelfTestingSuspicionOfSickness -> {
                        startQuestionnaireGuidelineFragment()
                    }
                    is HealthStatusData.ContactsSicknessInfo -> {
                        startInfectionInfoFragment()
                    }
                }
            },
            onRevokeSuspicionClick = {
                startReportingActivity(MessageType.Revoke.Suspicion)
            },
            onPresentMedicalReportClick = {
                startReportingActivity(MessageType.InfectionLevel.Red)
            },
            onCheckSymptomsAgainClick = {
                startQuestionnaireFragment()
            },
            onSomeoneHasRecoveredCloseClick = viewModel::someoneHasRecoveredSeen,
            onQuarantineEndCloseClick = viewModel::quarantineEndSeen,
            onAutomaticHandshakeEnabled = { enable ->
                viewModel.userWantsToRegisterAppForExposureNotifications = enable
//                viewModel.checkExposureNotificationPrerequisites(requireContext())
            },
            onShareAppClick = {
                shareApp()
            },
            onRevokeSicknessClick = {
                startReportingActivity(MessageType.Revoke.Sickness)
            }
        )
    }

    private val accurateScrollListener by lazy {
        AccurateScrollListener(
            onScroll = { scrolledDistance ->
                transparentAppBar.elevation = if (scrolledDistance > 0) {
                    requireContext().dipif(4)
                } else {
                    0f
                }
            }
        )
    }

    override fun onInitActionBar(actionBar: ActionBar?, toolbar: Toolbar?) {
        super.onInitActionBar(actionBar, toolbar)
        toolbar?.setNavigationIcon(R.drawable.ic_drawer)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(contentRecyclerView) {
            setController(controller)
            layoutManager = LinearLayoutManagerAccurateOffset(requireContext(), accurateScrollListener)
            addOnScrollListener(accurateScrollListener)
        }

        disposables += viewModel.observeOwnHealthStatus()
            .observeOnMainThread()
            .subscribe {
                controller.ownHealthStatus = it
            }

        disposables += viewModel.observeContactsHealthStatus()
            .observeOnMainThread()
            .subscribe {
                controller.contactsHealthStatus = it
            }

        disposables += viewModel.observeShowQuarantineEnd()
            .observeOnMainThread()
            .subscribe {
                controller.showQuarantineEnd = it
            }

        disposables += viewModel.observeSomeoneHasRecoveredStatus()
            .observeOnMainThread()
            .subscribe {
                controller.someoneHasRecoveredHealthStatus = it
            }

        disposables += viewModel.observeCombinedExposureNotificationState()
            .observeOnMainThread()
            .subscribe { state ->
                Timber.w("CombinedState = $state")
                controller.combinedExposureNotificationsState = state
                when (state) {
                    is EnabledWithError -> {
                        handleCombinedExposureStateError(state)
                    }
                }
            }

        controller.requestModelBuild()
    }

    override fun onResume() {
        super.onResume()
//        viewModel.refreshExposureNotificationAppRegisteredState()
    }

    override fun onDestroyView() {
        contentRecyclerView.removeOnScrollListener(accurateScrollListener)
        super.onDestroyView()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                startMenuFragment()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun handleCombinedExposureStateError(error: EnabledWithError) {
        when (error) {
            Prerequisites.UnavailableGooglePlayServices -> {
                GooglePlayServicesNotAvailableDialog().show()
            }
            Prerequisites.InvalidVersionOfGooglePlayServices -> {
                // TODO: 03/06/2020 dusanjencik: display dialog
            }
            is EnabledWithError.ExposureNotificationError -> {
                if (error.error is ApiException) {
                    if (error.error.statusCode == ExposureNotificationStatusCodes.RESOLUTION_REQUIRED) {
                        error.error.status.startResolutionForResult(
                            requireActivity(),
                            REQUEST_CODE_START_EXPOSURE_NOTIFICATION
                        )
                    } else {
                        handleBaseCoronaErrors(error.error)
                    }
                } else {
                    handleBaseCoronaErrors(error.error)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_START_EXPOSURE_NOTIFICATION -> {
                if (resultCode == Activity.RESULT_OK) {
                    viewModel.onExposureNotificationRegistrationResolutionResultOk()
                } else {
                    viewModel.onExposureNotificationRegistrationResolutionResultNotOk()
                }
            }
        }
    }
}