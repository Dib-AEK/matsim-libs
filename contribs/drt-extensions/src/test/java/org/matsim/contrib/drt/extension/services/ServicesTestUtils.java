package org.matsim.contrib.drt.extension.services;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.common.zones.systems.grid.square.SquareGridZoneSystemParams;
import org.matsim.contrib.drt.extension.DrtWithExtensionsConfigGroup;
import org.matsim.contrib.drt.extension.operations.DrtOperationsParams;
import org.matsim.contrib.drt.extension.operations.operationFacilities.OperationFacilitiesParams;
import org.matsim.contrib.drt.fare.DrtFareParams;
import org.matsim.contrib.drt.optimizer.constraints.DrtOptimizationConstraintsSetImpl;
import org.matsim.contrib.drt.optimizer.insertion.extensive.ExtensiveInsertionSearchParams;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingParams;
import org.matsim.contrib.drt.optimizer.rebalancing.mincostflow.MinCostFlowRebalancingStrategyParams;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.ev.EvConfigGroup;
import org.matsim.contrib.ev.EvConfigGroup.EvAnalysisOutput;
import org.matsim.contrib.zone.skims.DvrpTravelTimeMatrixParams;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.examples.ExamplesUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * @author steffenaxer
 */
public class ServicesTestUtils {


	public static Config configure(String outputDirectory,boolean electrified)
	{
		String fleetFile = "holzkirchenFleet.xml";
		String plansFile = "holzkirchenPlans.xml.gz";
		String networkFile = "holzkirchenNetwork.xml.gz";
		String opFacilitiesFile = "holzkirchenOperationFacilities.xml";
		String chargersFile =  "holzkirchenChargers.xml";
		String evsFile =  "holzkirchenElectricFleet.xml";

		MultiModeDrtConfigGroup multiModeDrtConfigGroup = new MultiModeDrtConfigGroup(DrtWithExtensionsConfigGroup::new);

		DrtWithExtensionsConfigGroup drtConfigGroup = (DrtWithExtensionsConfigGroup) multiModeDrtConfigGroup.createParameterSet("drt");

		drtConfigGroup.setMode(TransportMode.drt);
		DrtOptimizationConstraintsSetImpl defaultConstraintsSet =
                drtConfigGroup.addOrGetDrtOptimizationConstraintsParams()
                    .addOrGetDefaultDrtOptimizationConstraintsSet();
		drtConfigGroup.setStopDuration(30.);
		defaultConstraintsSet.setMaxTravelTimeAlpha(1.5);
		defaultConstraintsSet.setMaxTravelTimeBeta(10. * 60.);
		defaultConstraintsSet.setMaxWaitTime(600.);
		defaultConstraintsSet.setRejectRequestIfMaxWaitOrTravelTimeViolated(true);
		defaultConstraintsSet.setMaxWalkDistance(1000.);
		drtConfigGroup.setUseModeFilteredSubnetwork(false);
		drtConfigGroup.setVehiclesFile(fleetFile);
		drtConfigGroup.setOperationalScheme(DrtConfigGroup.OperationalScheme.door2door);
		drtConfigGroup.setPlotDetailedCustomerStats(true);
		drtConfigGroup.setIdleVehiclesReturnToDepots(false);

		drtConfigGroup.addParameterSet(new ExtensiveInsertionSearchParams());

		ConfigGroup rebalancing = drtConfigGroup.createParameterSet("rebalancing");
		drtConfigGroup.addParameterSet(rebalancing);
		((RebalancingParams) rebalancing).setInterval(600);

		MinCostFlowRebalancingStrategyParams strategyParams = new MinCostFlowRebalancingStrategyParams();
		strategyParams.setTargetAlpha(0.3);
		strategyParams.setTargetBeta(0.3);

		RebalancingParams rebalancingParams = drtConfigGroup.getRebalancingParams().get();
		rebalancingParams.addParameterSet(strategyParams);

		SquareGridZoneSystemParams zoneParams = (SquareGridZoneSystemParams) rebalancingParams.createParameterSet(SquareGridZoneSystemParams.SET_NAME);
		zoneParams.setCellSize(500.);
		rebalancingParams.addParameterSet(zoneParams);
		rebalancingParams.setTargetLinkSelection(RebalancingParams.TargetLinkSelection.mostCentral);

		multiModeDrtConfigGroup.addParameterSet(drtConfigGroup);

		DvrpConfigGroup dvrpConfigGroup = new DvrpConfigGroup();
		DvrpTravelTimeMatrixParams matrixParams = dvrpConfigGroup.getTravelTimeMatrixParams();
		matrixParams.addParameterSet(matrixParams.createParameterSet(SquareGridZoneSystemParams.SET_NAME));

		final Config config = ConfigUtils.createConfig(multiModeDrtConfigGroup,
			dvrpConfigGroup);
		config.setContext(ExamplesUtils.getTestScenarioURL("holzkirchen"));

		Set<String> modes = new HashSet<>();
		modes.add("drt");
		config.travelTimeCalculator().setAnalyzedModes(modes);

		ScoringConfigGroup.ModeParams scoreParams = new ScoringConfigGroup.ModeParams("drt");
		config.scoring().addModeParams(scoreParams);
		ScoringConfigGroup.ModeParams scoreParams2 = new ScoringConfigGroup.ModeParams("walk");
		config.scoring().addModeParams(scoreParams2);

		config.plans().setInputFile(plansFile);
		config.network().setInputFile(networkFile);

		config.qsim().setSimStarttimeInterpretation(QSimConfigGroup.StarttimeInterpretation.onlyUseStarttime);
		config.qsim().setSimEndtimeInterpretation(QSimConfigGroup.EndtimeInterpretation.minOfEndtimeAndMobsimFinished);

		final ScoringConfigGroup.ActivityParams home = new ScoringConfigGroup.ActivityParams("home");
		home.setTypicalDuration(8 * 3600);
		final ScoringConfigGroup.ActivityParams other = new ScoringConfigGroup.ActivityParams("other");
		other.setTypicalDuration(4 * 3600);
		final ScoringConfigGroup.ActivityParams education = new ScoringConfigGroup.ActivityParams("education");
		education.setTypicalDuration(6 * 3600);
		final ScoringConfigGroup.ActivityParams shopping = new ScoringConfigGroup.ActivityParams("shopping");
		shopping.setTypicalDuration(2 * 3600);
		final ScoringConfigGroup.ActivityParams work = new ScoringConfigGroup.ActivityParams("work");
		work.setTypicalDuration(2 * 3600);

		config.scoring().addActivityParams(home);
		config.scoring().addActivityParams(other);
		config.scoring().addActivityParams(education);
		config.scoring().addActivityParams(shopping);
		config.scoring().addActivityParams(work);

		final ReplanningConfigGroup.StrategySettings stratSets = new ReplanningConfigGroup.StrategySettings();
		stratSets.setWeight(1);
		stratSets.setStrategyName("ChangeExpBeta");
		config.replanning().addStrategySettings(stratSets);

		config.controller().setLastIteration(1);
		config.controller().setWriteEventsInterval(1);

		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controller().setOutputDirectory(outputDirectory);

		DrtOperationsParams operationsParams = (DrtOperationsParams) drtConfigGroup.createParameterSet(DrtOperationsParams.SET_NAME);
		OperationFacilitiesParams operationFacilitiesParams = (OperationFacilitiesParams) operationsParams.createParameterSet(OperationFacilitiesParams.SET_NAME);
		operationsParams.addParameterSet(operationFacilitiesParams);
		operationFacilitiesParams.setOperationFacilityInputFile(opFacilitiesFile);
		drtConfigGroup.addParameterSet(operationsParams);

		DrtFareParams drtFareParams = new DrtFareParams();
		drtFareParams.setBaseFare(1.);
		drtFareParams.setDistanceFare_m(1. / 1000);
		drtConfigGroup.addParameterSet(drtFareParams);

		if(electrified)
		{
			final EvConfigGroup evConfigGroup = new EvConfigGroup();
			evConfigGroup.setChargersFile(chargersFile);
			evConfigGroup.setMinimumChargeTime(0);
			evConfigGroup.setAnalysisOutputs(Set.of(EvAnalysisOutput.TimeProfiles));
			config.addModule(evConfigGroup);
		}

		config.vehicles().setVehiclesFile(evsFile);
		return config;
	}
}
