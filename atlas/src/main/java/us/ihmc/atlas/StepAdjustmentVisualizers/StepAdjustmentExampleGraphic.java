package us.ihmc.atlas.StepAdjustmentVisualizers;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.BipedSupportPolygons;
import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.YoPlaneContactState;
import us.ihmc.commonWalkingControlModules.configurations.ContinuousCMPICPPlannerParameters;
import us.ihmc.commonWalkingControlModules.configurations.ICPAngularMomentumModifierParameters;
import us.ihmc.commonWalkingControlModules.configurations.ICPWithTimeFreezingPlannerParameters;
import us.ihmc.commonWalkingControlModules.configurations.SteppingParameters;
import us.ihmc.commonWalkingControlModules.configurations.SwingTrajectoryParameters;
import us.ihmc.commonWalkingControlModules.configurations.ToeOffParameters;
import us.ihmc.commonWalkingControlModules.configurations.WalkingControllerParameters;
import us.ihmc.commonWalkingControlModules.desiredFootStep.footstepGenerator.FootstepTestHelper;
import us.ihmc.commonWalkingControlModules.instantaneousCapturePoint.ContinuousCMPBasedICPPlanner;
import us.ihmc.commonWalkingControlModules.instantaneousCapturePoint.ICPControlGains;
import us.ihmc.commonWalkingControlModules.instantaneousCapturePoint.ICPControlPlane;
import us.ihmc.commonWalkingControlModules.instantaneousCapturePoint.ICPControlPolygons;
import us.ihmc.commonWalkingControlModules.instantaneousCapturePoint.icpOptimization.recursiveController.ICPAdjustmentOptimizationController;
import us.ihmc.commonWalkingControlModules.instantaneousCapturePoint.icpOptimization.ICPOptimizationParameters;
import us.ihmc.commonWalkingControlModules.momentumBasedController.optimization.MomentumOptimizationSettings;
import us.ihmc.euclid.referenceFrame.FramePoint2D;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.FrameVector2D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.tuple2D.Point2D;
import us.ihmc.euclid.tuple2D.Vector2D;
import us.ihmc.graphicsDescription.Graphics3DObject;
import us.ihmc.graphicsDescription.appearance.AppearanceDefinition;
import us.ihmc.graphicsDescription.appearance.YoAppearance;
import us.ihmc.graphicsDescription.yoGraphics.BagOfBalls;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicPosition;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicShape;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.graphicsDescription.yoGraphics.plotting.YoArtifactPolygon;
import us.ihmc.humanoidRobotics.footstep.FootSpoof;
import us.ihmc.humanoidRobotics.footstep.Footstep;
import us.ihmc.humanoidRobotics.footstep.FootstepTiming;
import us.ihmc.robotics.controllers.PDGains;
import us.ihmc.robotics.controllers.pidGains.PIDSE3Gains;
import us.ihmc.robotics.geometry.ConvexPolygonScaler;
import us.ihmc.robotics.geometry.FrameConvexPolygon2d;
import us.ihmc.robotics.geometry.FramePose;
import us.ihmc.robotics.math.frames.YoFrameConvexPolygon2d;
import us.ihmc.robotics.math.frames.YoFramePoint2d;
import us.ihmc.robotics.math.frames.YoFramePose;
import us.ihmc.robotics.referenceFrames.MidFrameZUpFrame;
import us.ihmc.robotics.referenceFrames.ZUpFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.robotics.screwTheory.RigidBody;
import us.ihmc.sensorProcessing.stateEstimation.FootSwitchType;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.SimulationConstructionSet;
import us.ihmc.simulationconstructionset.gui.tools.SimulationOverheadPlotterFactory;
import us.ihmc.yoVariables.registry.YoVariableRegistry;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoDouble;

public class StepAdjustmentExampleGraphic
{
   private static final boolean switchType = true;
   private static final int numberOfFootstepsToCreate = 4;
   private static final int numberOfBalls = 100;
   private static final double controlDT = 0.001;

   private static final double pointFootDuration = 2.0;
   private static final double icpError = 0.05;

   private static final double footLengthForControl = 0.22;
   private static final double footWidthForControl = 0.11;
   private static final double toeWidthForControl = 0.0825;

   public static final Color defaultLeftColor = new Color(0.85f, 0.35f, 0.65f, 1.0f);
   public static final Color defaultRightColor = new Color(0.15f, 0.8f, 0.15f, 1.0f);

   private final YoVariableRegistry registry;

   private final SideDependentList<FootSpoof> contactableFeet = new SideDependentList<>();
   private final SideDependentList<ReferenceFrame> soleFrames = new SideDependentList<>();
   private final SideDependentList<ReferenceFrame> ankleFrames = new SideDependentList<>();
   private final SideDependentList<ReferenceFrame> ankleZUpFrames = new SideDependentList<>();
   private ReferenceFrame midFeetZUpFrame;

   private final SideDependentList<FramePose> footPosesAtTouchdown = new SideDependentList<FramePose>(new FramePose(), new FramePose());
   private final SideDependentList<YoFramePose> currentFootPoses = new SideDependentList<>();
   private final SideDependentList<YoPlaneContactState> contactStates = new SideDependentList<>();

   private final YoDouble doubleSupportDuration;
   private final YoDouble singleSupportDuration;
   private final YoDouble omega0;

   private final YoDouble yoTime;
   private final YoDouble timeToConsiderAdjustment;

   private final YoDouble pointFootFeedbackWeight;
   private final YoDouble pointFootFootstepWeight;

   private final YoFramePoint2d yoDesiredCMP;
   private final YoFramePoint2d yoCurrentICP;
   private final YoFramePoint2d yoDesiredICP;

   private final BagOfBalls bagOfBalls;

   private final ArrayList<Footstep> plannedFootsteps = new ArrayList<>();
   private final ArrayList<Footstep> footstepSolutions = new ArrayList<>();

   private final YoFramePose yoNextFootstepPlan;
   private final YoFramePose yoNextNextFootstepPlan;
   private final YoFramePose yoNextNextNextFootstepPlan;

   private final YoFramePose yoNextFootstepPose;
   private final YoFramePose yoNextNextFootstepPose;
   private final YoFramePose yoNextNextNextFootstepPose;
   private final YoFrameConvexPolygon2d yoNextFootstepPolygon;
   private final YoFrameConvexPolygon2d yoNextNextFootstepPolygon;
   private final YoFrameConvexPolygon2d yoNextNextNextFootstepPolygon;

   private final YoBoolean usePointFeet;

   private BipedSupportPolygons bipedSupportPolygons;
   private ICPControlPolygons icpControlPolygons;
   private FootstepTestHelper footstepTestHelper;

   private final ICPWithTimeFreezingPlannerParameters capturePointPlannerParameters;
   private final ICPOptimizationParameters icpOptimizationParameters;
   private final ICPAdjustmentOptimizationController icpOptimizationController;
   private final ContinuousCMPBasedICPPlanner icpPlanner;

   private final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();
   private final SimulationConstructionSet scs;

   public StepAdjustmentExampleGraphic()
   {
      Robot robot = new DummyRobot();
      YoGraphicsListRegistry yoGraphicsListRegistry = new YoGraphicsListRegistry();
      registry = robot.getRobotsYoVariableRegistry();

      WalkingControllerParameters walkingControllerParameters = createWalkingControllerParamters();
      capturePointPlannerParameters = createICPPlannerParameters();
      icpOptimizationParameters = createICPOptimizationParameters();

      usePointFeet = new YoBoolean("usePointFoot", registry);
      usePointFeet.set(true);

      pointFootFeedbackWeight = new YoDouble("pointFootFeedbackWeight", registry);
      pointFootFootstepWeight = new YoDouble("pointFootFootstepWeight", registry);
      pointFootFeedbackWeight.set(10000.0);
      pointFootFootstepWeight.set(0.1);

      yoDesiredCMP = new YoFramePoint2d("desiredCMP", worldFrame, registry);
      yoCurrentICP = new YoFramePoint2d("currentICP", worldFrame, registry);
      yoDesiredICP = new YoFramePoint2d("desiredICP", worldFrame, registry);

      doubleSupportDuration = new YoDouble("doubleSupportDuration", registry);
      singleSupportDuration = new YoDouble("singleSupportDuration", registry);
      omega0 = new YoDouble("omega0", registry);
      doubleSupportDuration.set(0.25);
      singleSupportDuration.set(0.75);
      omega0.set(3.0);

      yoTime = robot.getYoTime();
      timeToConsiderAdjustment = new YoDouble("timeToConsiderAdjustment", registry);
      timeToConsiderAdjustment.set(0.5 * singleSupportDuration.getDoubleValue());

      setupFeetFrames(yoGraphicsListRegistry);

      icpPlanner = new ContinuousCMPBasedICPPlanner(bipedSupportPolygons, contactableFeet, capturePointPlannerParameters.getNumberOfFootstepsToConsider(),
                                                    registry, yoGraphicsListRegistry);
      icpPlanner.initializeParameters(capturePointPlannerParameters);
      icpPlanner.setOmega0(omega0.getDoubleValue());

      icpOptimizationController = new ICPAdjustmentOptimizationController(capturePointPlannerParameters, icpOptimizationParameters, walkingControllerParameters,
                                                                          bipedSupportPolygons, icpControlPolygons,
            contactableFeet, controlDT, registry, yoGraphicsListRegistry);

      RobotSide currentSide = RobotSide.LEFT;
      for (int i = 0; i < numberOfFootstepsToCreate; i++)
      {
         currentSide = currentSide.getOppositeSide();

         plannedFootsteps.add(new Footstep(currentSide));
         footstepSolutions.add(new Footstep(currentSide));
      }


      yoNextFootstepPlan = new YoFramePose("nextFootstepPlan", worldFrame, registry);
      yoNextNextFootstepPlan = new YoFramePose("nextNextFootstepPlan", worldFrame, registry);
      yoNextNextNextFootstepPlan = new YoFramePose("nextNextNextFootstepPlan", worldFrame, registry);

      yoNextFootstepPose = new YoFramePose("nextFootstepPose", worldFrame, registry);
      yoNextNextFootstepPose = new YoFramePose("nextNextFootstepPose", worldFrame, registry);
      yoNextNextNextFootstepPose = new YoFramePose("nextNextNextFootstepPose", worldFrame, registry);

      yoNextFootstepPolygon = new YoFrameConvexPolygon2d("nextFootstep", "", worldFrame, 4, registry);
      yoNextNextFootstepPolygon = new YoFrameConvexPolygon2d("nextNextFootstep", "", worldFrame, 4, registry);
      yoNextNextNextFootstepPolygon = new YoFrameConvexPolygon2d("nextNextNextFootstep", "", worldFrame, 4, registry);

      Graphics3DObject footstepGraphics = new Graphics3DObject();
      List<Point2D> contactPoints = new ArrayList<>();
      for (FramePoint2D point : contactableFeet.get(RobotSide.LEFT).getContactPoints2d())
         contactPoints.add(new Point2D(point));
      footstepGraphics.addExtrudedPolygon(contactPoints, 0.02, YoAppearance.Color(Color.blue));

      YoGraphicShape nextFootstepViz = new YoGraphicShape("nextFootstep", footstepGraphics, yoNextFootstepPose, 1.0);
      YoGraphicShape nextNextFootstepViz = new YoGraphicShape("nextNextFootstep", footstepGraphics, yoNextNextFootstepPose, 1.0);
      YoGraphicShape nextNextNextFootstepViz = new YoGraphicShape("nextNextNextFootstep", footstepGraphics, yoNextNextNextFootstepPose, 1.0);


      YoArtifactPolygon nextFootstepArtifact =  new YoArtifactPolygon("nextFootstep", yoNextFootstepPolygon, Color.blue, false);
      YoArtifactPolygon nextNextFootstepArtifact =  new YoArtifactPolygon("nextNextFootstep", yoNextNextFootstepPolygon, Color.blue, false);
      YoArtifactPolygon nextNextNextFootstepArtifact =  new YoArtifactPolygon("nextNextNextFootstep", yoNextNextNextFootstepPolygon, Color.blue, false);

      YoGraphicPosition desiredCMPViz = new YoGraphicPosition("Desired CMP", yoDesiredCMP, 0.012, YoAppearance.Red(), YoGraphicPosition.GraphicType.BALL_WITH_CROSS);
      YoGraphicPosition desiredICPViz = new YoGraphicPosition("Desired ICP", yoDesiredICP, 0.01, YoAppearance.LightBlue(), YoGraphicPosition.GraphicType.BALL_WITH_CROSS);
      YoGraphicPosition currentICPViz = new YoGraphicPosition("Current ICP", yoCurrentICP, 0.01, YoAppearance.Blue(), YoGraphicPosition.GraphicType.BALL_WITH_CROSS);

      bagOfBalls = new BagOfBalls(numberOfBalls, 0.002, YoAppearance.Blue(), YoGraphicPosition.GraphicType.SOLID_BALL, registry, yoGraphicsListRegistry);

      yoGraphicsListRegistry.registerYoGraphic("dummy", nextFootstepViz);
      yoGraphicsListRegistry.registerYoGraphic("dummy", nextNextFootstepViz);
      yoGraphicsListRegistry.registerYoGraphic("dummy", nextNextNextFootstepViz);

      yoGraphicsListRegistry.registerArtifact("dummy", desiredCMPViz.createArtifact());
      yoGraphicsListRegistry.registerArtifact("dummy", desiredICPViz.createArtifact());
      yoGraphicsListRegistry.registerArtifact("dummy", currentICPViz.createArtifact());
      yoGraphicsListRegistry.registerArtifact("dummy", nextFootstepArtifact);
      yoGraphicsListRegistry.registerArtifact("dummy", nextNextFootstepArtifact);
      yoGraphicsListRegistry.registerArtifact("dummy", nextNextNextFootstepArtifact);

      scs = new SimulationConstructionSet(robot);
      SimulationOverheadPlotterFactory simulationOverheadPlotterFactory = scs.createSimulationOverheadPlotterFactory();
      simulationOverheadPlotterFactory.setShowOnStart(true);
      simulationOverheadPlotterFactory.addYoGraphicsListRegistries(yoGraphicsListRegistry);
      simulationOverheadPlotterFactory.createOverheadPlotter();

      Thread myThread = new Thread(scs);
      myThread.start();
   }

   private void setupFeetFrames(YoGraphicsListRegistry yoGraphicsListRegistry)
   {
      for (RobotSide robotSide : RobotSide.values)
      {
         String sidePrefix = robotSide.getCamelCaseNameForStartOfExpression();
         double xToAnkle = 0.0;
         double yToAnkle = 0.0;
         double zToAnkle = 0.0;
         List<Point2D> contactPointsInSoleFrame = new ArrayList<>();
         contactPointsInSoleFrame.add(new Point2D(footLengthForControl / 2.0, toeWidthForControl / 2.0));
         contactPointsInSoleFrame.add(new Point2D(footLengthForControl / 2.0, -toeWidthForControl / 2.0));
         contactPointsInSoleFrame.add(new Point2D(-footLengthForControl / 2.0, -footWidthForControl / 2.0));
         contactPointsInSoleFrame.add(new Point2D(-footLengthForControl / 2.0, footWidthForControl / 2.0));
         FootSpoof contactableFoot = new FootSpoof(sidePrefix + "Foot", xToAnkle, yToAnkle, zToAnkle, contactPointsInSoleFrame, 0.0);
         FramePose startingPose = footPosesAtTouchdown.get(robotSide);
         startingPose.setToZero(worldFrame);
         startingPose.setY(robotSide.negateIfRightSide(0.15));
         contactableFoot.setSoleFrame(startingPose);
         contactableFeet.put(robotSide, contactableFoot);

         currentFootPoses.put(robotSide, new YoFramePose(sidePrefix + "FootPose", worldFrame, registry));

         Graphics3DObject footGraphics = new Graphics3DObject();
         AppearanceDefinition footColor = robotSide == RobotSide.LEFT ? YoAppearance.Color(defaultLeftColor) : YoAppearance.Color(defaultRightColor);
         footGraphics.addExtrudedPolygon(contactPointsInSoleFrame, 0.02, footColor);
         yoGraphicsListRegistry.registerYoGraphic("FootViz", new YoGraphicShape(sidePrefix + "FootViz", footGraphics, currentFootPoses.get(robotSide), 1.0));
      }

      for (RobotSide robotSide : RobotSide.values)
      {
         String sidePrefix = robotSide.getCamelCaseNameForStartOfExpression();
         FootSpoof contactableFoot = contactableFeet.get(robotSide);
         RigidBody foot = contactableFoot.getRigidBody();
         ReferenceFrame soleFrame = contactableFoot.getSoleFrame();
         List<FramePoint2D> contactFramePoints = contactableFoot.getContactPoints2d();
         double coefficientOfFriction = contactableFoot.getCoefficientOfFriction();
         YoPlaneContactState yoPlaneContactState = new YoPlaneContactState(sidePrefix + "Foot", foot, soleFrame, contactFramePoints, coefficientOfFriction, registry);
         yoPlaneContactState.setFullyConstrained();
         contactStates.put(robotSide, yoPlaneContactState);
      }

      for (RobotSide robotSide : RobotSide.values)
      {
         FootSpoof contactableFoot = contactableFeet.get(robotSide);
         ReferenceFrame ankleFrame = contactableFoot.getFrameAfterParentJoint();
         ankleFrames.put(robotSide, ankleFrame);
         ankleZUpFrames.put(robotSide, new ZUpFrame(worldFrame, ankleFrame, robotSide.getCamelCaseNameForStartOfExpression() + "ZUp"));
         soleFrames.put(robotSide, contactableFoot.getSoleFrame());
      }

      midFeetZUpFrame = new MidFrameZUpFrame("midFeetZupFrame", worldFrame, ankleZUpFrames.get(RobotSide.LEFT), ankleZUpFrames.get(RobotSide.RIGHT));
      midFeetZUpFrame.update();
      bipedSupportPolygons = new BipedSupportPolygons(ankleZUpFrames, midFeetZUpFrame, ankleZUpFrames, registry, yoGraphicsListRegistry);
      ICPControlPlane icpControlPlane = new ICPControlPlane(omega0, ReferenceFrame.getWorldFrame(), 9.81, registry);
      icpControlPolygons = new ICPControlPolygons(icpControlPlane, midFeetZUpFrame, registry, yoGraphicsListRegistry);

      footstepTestHelper = new FootstepTestHelper(contactableFeet);

   }

   private double initialTime;
   private final FootstepTiming timing = new FootstepTiming();
   private void initialize()
   {
      int index = 0;
      for (Footstep footstep : footstepTestHelper.createFootsteps(0.2, 0.4, numberOfFootstepsToCreate))
      {
         footstep.getPose(footstepPose);
         plannedFootsteps.get(index).setPose(footstepPose);
         footstepSolutions.get(index).setPose(footstepPose);

         index++;
      }

      /** fake transfer **/
      for (RobotSide robotSide : RobotSide.values)
         contactStates.get(robotSide).setFullyConstrained();
      bipedSupportPolygons.updateUsingContactStates(contactStates);
      icpControlPolygons.updateUsingContactStates(contactStates);

      icpPlanner.clearPlan();
      timing.setTimings(doubleSupportDuration.getDoubleValue(), singleSupportDuration.getDoubleValue());
      icpPlanner.addFootstepToPlan(plannedFootsteps.get(0), timing);
      icpPlanner.addFootstepToPlan(plannedFootsteps.get(1), timing);
      icpPlanner.addFootstepToPlan(plannedFootsteps.get(2), timing);

      icpOptimizationController.clearPlan();
      icpOptimizationController.addFootstepToPlan(plannedFootsteps.get(0), timing);
      icpOptimizationController.addFootstepToPlan(plannedFootsteps.get(1), timing);
      icpOptimizationController.addFootstepToPlan(plannedFootsteps.get(2), timing);

      RobotSide supportSide = plannedFootsteps.get(0).getRobotSide().getOppositeSide();

      icpPlanner.setSupportLeg(supportSide);
      icpPlanner.initializeForTransfer(yoTime.getDoubleValue());
      icpPlanner.compute(yoTime.getDoubleValue() + doubleSupportDuration.getDoubleValue());
      icpPlanner.getDesiredCapturePointPosition(desiredICP);
      icpPlanner.getDesiredCapturePointVelocity(desiredICPVelocity);

      icpOptimizationController.initializeForTransfer(yoTime.getDoubleValue(), supportSide, omega0.getDoubleValue());
      icpOptimizationController.compute(yoTime.getDoubleValue() + doubleSupportDuration.getDoubleValue(), desiredICP, desiredICPVelocity, perfectCMP, desiredICP, omega0.getDoubleValue());

      /** do single support **/

      FootSpoof footSpoof = contactableFeet.get(supportSide.getOppositeSide());
      FramePose nextSupportPose = footPosesAtTouchdown.get(supportSide.getOppositeSide());
      nextSupportPose.setToZero(plannedFootsteps.get(0).getSoleReferenceFrame());
      nextSupportPose.changeFrame(ReferenceFrame.getWorldFrame());
      footSpoof.setSoleFrame(nextSupportPose);

      contactStates.get(supportSide.getOppositeSide()).clear();
      if (plannedFootsteps.get(0).getPredictedContactPoints() == null)
         contactStates.get(supportSide.getOppositeSide()).setContactFramePoints(footSpoof.getContactPoints2d());
      else
         contactStates.get(supportSide.getOppositeSide()).setContactPoints(plannedFootsteps.get(0).getPredictedContactPoints());
      bipedSupportPolygons.updateUsingContactStates(contactStates);
      icpControlPolygons.updateUsingContactStates(contactStates);

      icpPlanner.clearPlan();
      timing.setTimings(singleSupportDuration.getDoubleValue(), doubleSupportDuration.getDoubleValue());
      icpPlanner.addFootstepToPlan(plannedFootsteps.get(0), timing);
      icpPlanner.addFootstepToPlan(plannedFootsteps.get(1), timing);
      icpPlanner.addFootstepToPlan(plannedFootsteps.get(2), timing);

      icpOptimizationController.clearPlan();
      icpOptimizationController.addFootstepToPlan(plannedFootsteps.get(0), timing);
      icpOptimizationController.addFootstepToPlan(plannedFootsteps.get(1), timing);
      icpOptimizationController.addFootstepToPlan(plannedFootsteps.get(2), timing);

      icpPlanner.setSupportLeg(supportSide);
      icpPlanner.initializeForSingleSupport(yoTime.getDoubleValue());

      icpOptimizationController.initializeForSingleSupport(yoTime.getDoubleValue(), supportSide, omega0.getDoubleValue());

      icpPlanner.compute(yoTime.getDoubleValue());
      icpPlanner.getDesiredCapturePointPosition(desiredICP);
      icpPlanner.getDesiredCapturePointVelocity(desiredICPVelocity);
      icpOptimizationController.setBeginningOfStateICP(desiredICP, desiredICPVelocity);

      icpPlanner.compute(yoTime.getDoubleValue() + timeToConsiderAdjustment.getDoubleValue());
      icpPlanner.getDesiredCapturePointPosition(desiredICP);
      icpPlanner.getDesiredCapturePointVelocity(desiredICPVelocity);
      yoCurrentICP.set(desiredICP);

      initialTime = yoTime.getDoubleValue();
      updateViz();
   }

   double timeForUpdate = 0.0;
   double initialPrimeTime = 0.0;
   int segmentNumber = 0;

   public void updateCurrentICPPosition()
   {
      timeForUpdate = yoTime.getDoubleValue() - initialPrimeTime - initialTime;

      double segmentDuration;
      if (segmentNumber == 1)
         segmentDuration = 2 * pointFootDuration;
      else
         segmentDuration = pointFootDuration;

      if (timeForUpdate > segmentDuration)
      {
         timeForUpdate = 0.0;
         initialPrimeTime = yoTime.getDoubleValue() + initialTime;

         segmentNumber++;

         if (segmentNumber > 2)
         {
            segmentNumber = 0;

            if (switchType)
            {
               if (usePointFeet.getBooleanValue())
                  usePointFeet.set(false);
               else
                  usePointFeet.set(true);
            }
         }
      }

      if (segmentNumber == 0)
      {
         double scaleFactor = timeForUpdate / segmentDuration;
         yoCurrentICP.set(yoDesiredICP);
         yoCurrentICP.add(scaleFactor * icpError, scaleFactor * icpError);
      }
      else if (segmentNumber == 1)
      {
         double scaleFactor = timeForUpdate / segmentDuration;
         yoCurrentICP.set(yoDesiredICP);
         yoCurrentICP.add(icpError, icpError);
         yoCurrentICP.add(-scaleFactor * 2 * icpError, -scaleFactor * 2 * icpError);
      }
      else if (segmentNumber == 2)
      {
         double scaleFactor = timeForUpdate / segmentDuration;
         yoCurrentICP.set(yoDesiredICP);
         yoCurrentICP.add(-icpError, -icpError);
         yoCurrentICP.add(scaleFactor * icpError, scaleFactor * icpError);
      }

      if (usePointFeet.getBooleanValue())
      {
         icpOptimizationController.setFootstepWeights(pointFootFootstepWeight.getDoubleValue(), pointFootFootstepWeight.getDoubleValue());
         icpOptimizationController.setFeedbackWeights(pointFootFeedbackWeight.getDoubleValue(), pointFootFeedbackWeight.getDoubleValue());
      }
      else
      {
         icpOptimizationController.setFootstepWeights(100.0, 100.0);
         icpOptimizationController.setFeedbackWeights(1.0, 1.0);
      }
   }

   private boolean firstTick = true;

   private final FramePose footstepPose = new FramePose();
   private final FramePoint2D footstepPositionSolution = new FramePoint2D();
   private final FramePoint2D desiredCMP = new FramePoint2D();
   private final FramePoint2D desiredICP = new FramePoint2D();
   private final FrameVector2D desiredICPVelocity = new FrameVector2D();
   private final FramePoint2D currentICP = new FramePoint2D();
   private final FramePoint2D perfectCMP = new FramePoint2D();

   public void updateGraphic()
   {
      if (firstTick)
      {
         initialize();
         firstTick = false;
      }

      double currentTime = timeToConsiderAdjustment.getDoubleValue() + initialTime;
      icpPlanner.compute(currentTime);
      icpPlanner.getDesiredCapturePointPosition(desiredICP);
      icpPlanner.getDesiredCapturePointVelocity(desiredICPVelocity);
      yoDesiredICP.set(desiredICP);

      updateCurrentICPPosition();

      yoCurrentICP.getFrameTuple2d(currentICP);
      icpOptimizationController.compute(currentTime, desiredICP, desiredICPVelocity, perfectCMP, currentICP, omega0.getDoubleValue());
      icpOptimizationController.getDesiredCMP(desiredCMP);
      yoDesiredCMP.set(desiredCMP);

      //// FIXME: 2/27/17  This is messing things up
      for (int i = 0; i < icpOptimizationController.getNumberOfFootstepsToConsider(); i++)
      {
         Footstep footstep = footstepSolutions.get(i);

         if (footstep != null)
         {
            footstep.getPose(footstepPose);
            icpOptimizationController.getFootstepSolution(i, footstepPositionSolution);
            footstepPose.setXYFromPosition2d(footstepPositionSolution);
            footstep.setPose(footstepPose);
         }
      }

      updateViz();
      updateTrajectoyViz(); //// FIXME: 2/27/17
   }


   private final FramePose nextPlannedFootstep = new FramePose();
   private final FramePose nextNextPlannedFootstep = new FramePose();
   private final FramePose nextNextNextPlannedFootstep = new FramePose();

   private final FrameConvexPolygon2d footstepPolygon = new FrameConvexPolygon2d();
   private final FrameConvexPolygon2d tempFootstepPolygonForShrinking = new FrameConvexPolygon2d();
   private final ConvexPolygonScaler convexPolygonShrinker = new ConvexPolygonScaler();

   private void updateViz()
   {
      nextPlannedFootstep.setToZero(plannedFootsteps.get(0).getSoleReferenceFrame());
      nextNextPlannedFootstep.setToZero(plannedFootsteps.get(0).getSoleReferenceFrame());
      nextNextNextPlannedFootstep.setToZero(plannedFootsteps.get(0).getSoleReferenceFrame());
      yoNextFootstepPlan.setAndMatchFrame(nextPlannedFootstep);
      yoNextNextFootstepPlan.setAndMatchFrame(nextNextPlannedFootstep);
      yoNextNextNextFootstepPlan.setAndMatchFrame(nextNextNextPlannedFootstep);

      if (footstepSolutions.get(0) == null)
      {
         yoNextFootstepPose.setToNaN();
         yoNextNextFootstepPose.setToNaN();
         yoNextNextNextFootstepPose.setToNaN();
         yoNextFootstepPolygon.hide();
         yoNextNextFootstepPolygon.hide();
         yoNextNextNextFootstepPolygon.hide();
         return;
      }

      if (footstepSolutions.get(0).getPredictedContactPoints() == null)
         footstepSolutions.get(0).setPredictedContactPoints(contactableFeet.get(footstepSolutions.get(0).getRobotSide()).getContactPoints2d());

      double polygonShrinkAmount = 0.005;

      tempFootstepPolygonForShrinking.setIncludingFrameAndUpdate(footstepSolutions.get(0).getSoleReferenceFrame(), footstepSolutions.get(0).getPredictedContactPoints());
      convexPolygonShrinker.scaleConvexPolygon(tempFootstepPolygonForShrinking, polygonShrinkAmount, footstepPolygon);

      footstepPolygon.changeFrameAndProjectToXYPlane(worldFrame);
      yoNextFootstepPolygon.setFrameConvexPolygon2d(footstepPolygon);

      FramePose nextFootstepPose = new FramePose(footstepSolutions.get(0).getSoleReferenceFrame());
      yoNextFootstepPose.setAndMatchFrame(nextFootstepPose);

      if (footstepSolutions.get(1) == null)
      {
         yoNextNextFootstepPose.setToNaN();
         yoNextNextNextFootstepPose.setToNaN();
         yoNextNextFootstepPolygon.hide();
         yoNextNextNextFootstepPolygon.hide();
         return;
      }

      if (footstepSolutions.get(1).getPredictedContactPoints() == null)
         footstepSolutions.get(1).setPredictedContactPoints(contactableFeet.get(footstepSolutions.get(1).getRobotSide()).getContactPoints2d());

      tempFootstepPolygonForShrinking.setIncludingFrameAndUpdate(footstepSolutions.get(1).getSoleReferenceFrame(), footstepSolutions.get(1).getPredictedContactPoints());
      convexPolygonShrinker.scaleConvexPolygon(tempFootstepPolygonForShrinking, polygonShrinkAmount, footstepPolygon);

      footstepPolygon.changeFrameAndProjectToXYPlane(worldFrame);
      yoNextNextFootstepPolygon.setFrameConvexPolygon2d(footstepPolygon);

      FramePose nextNextFootstepPose = new FramePose(footstepSolutions.get(1).getSoleReferenceFrame());
      yoNextNextFootstepPose.setAndMatchFrame(nextNextFootstepPose);

      if (footstepSolutions.get(2) == null)
      {
         yoNextNextNextFootstepPose.setToNaN();
         yoNextNextNextFootstepPolygon.hide();
         return;
      }

      if (footstepSolutions.get(2).getPredictedContactPoints() == null)
         footstepSolutions.get(2).setPredictedContactPoints(contactableFeet.get(footstepSolutions.get(2).getRobotSide()).getContactPoints2d());

      tempFootstepPolygonForShrinking.setIncludingFrameAndUpdate(footstepSolutions.get(2).getSoleReferenceFrame(), footstepSolutions.get(2).getPredictedContactPoints());
      convexPolygonShrinker.scaleConvexPolygon(tempFootstepPolygonForShrinking, polygonShrinkAmount, footstepPolygon);

      footstepPolygon.changeFrameAndProjectToXYPlane(worldFrame);
      yoNextNextNextFootstepPolygon.setFrameConvexPolygon2d(footstepPolygon);

      FramePose nextNextNextFootstepPose = new FramePose(footstepSolutions.get(2).getSoleReferenceFrame());
      yoNextNextNextFootstepPose.setAndMatchFrame(nextNextNextFootstepPose);

      RobotSide supportSide = footstepSolutions.get(0).getRobotSide().getOppositeSide();
      FootSpoof footSpoof = contactableFeet.get(supportSide.getOppositeSide());
      FramePose nextSupportPose = footPosesAtTouchdown.get(supportSide.getOppositeSide());
      nextSupportPose.setToZero(footstepSolutions.get(0).getSoleReferenceFrame());
      nextSupportPose.changeFrame(ReferenceFrame.getWorldFrame());
      footSpoof.setSoleFrame(nextSupportPose);
   }

   private final FramePoint3D desiredICP3d = new FramePoint3D();
   public void updateTrajectoyViz()
   {
      icpPlanner.clearPlan();
      timing.setTimings(singleSupportDuration.getDoubleValue(), doubleSupportDuration.getDoubleValue());
      icpPlanner.addFootstepToPlan(footstepSolutions.get(0), timing);
      icpPlanner.addFootstepToPlan(footstepSolutions.get(1), timing);
      icpPlanner.addFootstepToPlan(footstepSolutions.get(2), timing);

      icpPlanner.setSupportLeg(plannedFootsteps.get(0).getRobotSide().getOppositeSide());
      icpPlanner.initializeForSingleSupport(initialTime);

      double trajectoryDT = singleSupportDuration.getDoubleValue() / numberOfBalls;

      for (int i = 0; i < numberOfBalls; i++)
      {
         double currentTime = i * trajectoryDT + initialTime;
         icpPlanner.compute(currentTime);
         icpPlanner.getDesiredCapturePointPosition(desiredICP);
         icpPlanner.getDesiredCapturePointVelocity(desiredICPVelocity);
         desiredICP3d.set(desiredICP, 0.0);
         bagOfBalls.setBall(desiredICP3d, i);
      }

      icpPlanner.clearPlan();
      timing.setTimings(singleSupportDuration.getDoubleValue(), doubleSupportDuration.getDoubleValue());
      icpPlanner.addFootstepToPlan(plannedFootsteps.get(0), timing);
      icpPlanner.addFootstepToPlan(plannedFootsteps.get(1), timing);
      icpPlanner.addFootstepToPlan(plannedFootsteps.get(2), timing);

      icpPlanner.setSupportLeg(plannedFootsteps.get(0).getRobotSide().getOppositeSide());
      icpPlanner.initializeForSingleSupport(initialTime);
   }

   public static void main(String[] args)
   {
      StepAdjustmentExampleGraphic stepAdjustmentExampleGraphic = new StepAdjustmentExampleGraphic();
   }

   private class DummyRobot extends  Robot
   {
      public DummyRobot()
      {
         super("dummyRobot");
      }

      @Override
      public void update()
      {
         super.update();
         updateGraphic();
      }

   }

   private WalkingControllerParameters createWalkingControllerParamters()
   {
      return new WalkingControllerParameters()
      {
         @Override
         public double getOmega0()
         {
            return 0;
         }

         @Override
         public double getMaximumLegLengthForSingularityAvoidance()
         {
            return 0;
         }

         @Override
         public double minimumHeightAboveAnkle()
         {
            return 0;
         }

         @Override
         public double nominalHeightAboveAnkle()
         {
            return 0;
         }

         @Override
         public double maximumHeightAboveAnkle()
         {
            return 0;
         }

         @Override
         public double defaultOffsetHeightAboveAnkle()
         {
            return 0;
         }

         @Override
         public boolean allowDisturbanceRecoveryBySpeedingUpSwing()
         {
            return false;
         }

         @Override
         public boolean allowAutomaticManipulationAbort()
         {
            return false;
         }

         @Override
         public double getMinimumSwingTimeForDisturbanceRecovery()
         {
            return 0;
         }

         @Override
         public boolean useOptimizationBasedICPController()
         {
            return false;
         }

         @Override
         public double getICPErrorThresholdToSpeedUpSwing()
         {
            return 0;
         }

         @Override
         public ICPControlGains createICPControlGains()
         {
            return null;
         }

         @Override
         public PDGains getCoMHeightControlGains()
         {
            return null;
         }

         @Override
         public PIDSE3Gains getSwingFootControlGains()
         {
            return null;
         }

         @Override
         public PIDSE3Gains getHoldPositionFootControlGains()
         {
            return null;
         }

         @Override
         public PIDSE3Gains getToeOffFootControlGains()
         {
            return null;
         }

         @Override
         public double getDefaultTransferTime()
         {
            return 0;
         }

         @Override
         public double getDefaultSwingTime()
         {
            return 0;
         }

         @Override
         public double getContactThresholdForce()
         {
            return 0;
         }

         @Override
         public double getSecondContactThresholdForceIgnoringCoP()
         {
            return 0;
         }

         @Override
         public double getCoPThresholdFraction()
         {
            return 0;
         }

         @Override
         public String[] getJointsToIgnoreInController()
         {
            return new String[0];
         }

         @Override
         public MomentumOptimizationSettings getMomentumOptimizationSettings()
         {
            return null;
         }

         @Override
         public ICPAngularMomentumModifierParameters getICPAngularMomentumModifierParameters()
         {
            return null;
         }

         @Override
         public FootSwitchType getFootSwitchType()
         {
            return null;
         }

         @Override
         public double getContactThresholdHeight()
         {
            return 0;
         }

         @Override
         public double getMaxICPErrorBeforeSingleSupportX()
         {
            return 0;
         }

         @Override
         public double getMaxICPErrorBeforeSingleSupportY()
         {
            return 0;
         }

         @Override
         public boolean finishSingleSupportWhenICPPlannerIsDone()
         {
            return false;
         }

         @Override
         public double getHighCoPDampingDurationToPreventFootShakies()
         {
            return 0;
         }

         @Override
         public double getCoPErrorThresholdForHighCoPDamping()
         {
            return 0;
         }

         @Override
         public ToeOffParameters getToeOffParameters()
         {
            return new ToeOffParameters()
            {
               @Override
               public boolean doToeOffIfPossible()
               {
                  return false;
               }

               @Override
               public boolean doToeOffIfPossibleInSingleSupport()
               {
                  return false;
               }

               @Override
               public boolean checkECMPLocationToTriggerToeOff()
               {
                  return false;
               }

               @Override
               public double getMinStepLengthForToeOff()
               {
                  return 0;
               }

               @Override
               public boolean doToeOffWhenHittingAnkleLimit()
               {
                  return false;
               }

               @Override
               public double getMaximumToeOffAngle()
               {
                  return 0;
               }
            };
         }

         @Override
         public SwingTrajectoryParameters getSwingTrajectoryParameters()
         {
            return null;
         }

         @Override
         public SteppingParameters getSteppingParameters()
         {
            return null;
         }
      };
   }

   private ICPWithTimeFreezingPlannerParameters createICPPlannerParameters()
   {
      return new ContinuousCMPICPPlannerParameters()
      {
         /** {@inheritDoc} */
         @Override
         public List<Vector2D> getCoPOffsets()
         {

            Vector2D entryOffset = new Vector2D(0.0, -0.005);
            Vector2D exitOffset = new Vector2D(0.0, 0.025);

            List<Vector2D> copOffsets = new ArrayList<>();
            copOffsets.add(entryOffset);
            copOffsets.add(exitOffset);

            return copOffsets;
         }

         /** {@inheritDoc} */
         @Override
         public int getNumberOfCoPWayPointsPerFoot()
         {
            return 2;
         }

         /** {@inheritDoc} */
         @Override
         public List<Vector2D> getCoPForwardOffsetBounds()
         {
            Vector2D entryBounds = new Vector2D(0.0, 0.03);
            Vector2D exitBounds = new Vector2D(-0.04, 0.08);

            List<Vector2D> copForwardOffsetBounds = new ArrayList<>();
            copForwardOffsetBounds.add(entryBounds);
            copForwardOffsetBounds.add(exitBounds);

            return copForwardOffsetBounds;
         }
      };
   }

   public ICPOptimizationParameters createICPOptimizationParameters()
   {
      return new ICPOptimizationParameters()
      {
         @Override
         public boolean useSimpleOptimization()
         {
            return false;
         }

         @Override public int getMaximumNumberOfFootstepsToConsider()
         {
            return 5;
         }

         @Override
         public int numberOfFootstepsToConsider()
         {
            return 1;
         }

         @Override public double getFootstepRegularizationWeight()
         {
            return 0.01;
         }

         @Override public double getFeedbackRegularizationWeight()
         {
            return 0.0001;
         }

         @Override public double getForwardFootstepWeight()
         {
            return 100.0;
         }

         @Override public double getLateralFootstepWeight()
         {
            return 100.0;
         }

         @Override public double getFeedbackForwardWeight()
         {
            return 0.1;
         }

         @Override public double getFeedbackLateralWeight()
         {
            return 0.1;
         }

         @Override
         public boolean useDifferentSplitRatioForBigAdjustment()
         {
            return true;
         }

         @Override
         public double getMagnitudeForBigAdjustment()
         {
            return 0.2;
         }

         @Override public double getFeedbackParallelGain()
         {
            return 2.5;
         }

         @Override public double getFeedbackOrthogonalGain()
         {
            return 3.0;
         }

         @Override
         public double getFootstepSolutionResolution()
         {
            return 0.0;
         }

         @Override
         public double getLateralReachabilityOuterLimit()
         {
            return 1.0;
         }

         @Override
         public double getLateralReachabilityInnerLimit()
         {
            return -0.15;
         }

         @Override
         public double getForwardReachabilityLimit()
         {
            return 1.2;
         }

         @Override
         public double getBackwardReachabilityLimit()
         {
            return -0.6;
         }

         @Override public double getDynamicRelaxationWeight()
         {
            return 10000.0;
         }

         @Override public double getDynamicRelaxationDoubleSupportWeightModifier()
         {
            return 5.0;
         }

         @Override public double getAngularMomentumMinimizationWeight()
         {
            return 500.0;
         }

         @Override public boolean useFeedbackRegularization()
         {
            return true;
         }

         @Override public boolean useStepAdjustment()
         {
            return true;
         }

         @Override public boolean useAngularMomentum()
         {
            return false;
         }

         @Override public boolean useTimingOptimization()
         {
            return false;
         }

         @Override public boolean useFootstepRegularization()
         {
            return true;
         }

         @Override public boolean scaleStepRegularizationWeightWithTime()
         {
            return true;
         }

         @Override public boolean scaleUpcomingStepWeights()
         {
            return true;
         }

         @Override public boolean scaleFeedbackWeightWithGain()
         {
            return true;
         }

         @Override public double getMinimumFootstepWeight()
         {
            return 0.0001;
         }

         @Override public double getMinimumFeedbackWeight()
         {
            return 0.0001;
         }

         @Override public double getMinimumTimeRemaining()
         {
            return 0.001;
         }

         @Override public double getAdjustmentDeadband()
         {
            return 0.02;
         }
      };
   }
}
