package fleetClient;

import aima.core.util.datastructure.Pair;


import io.grpc.Channel;
import io.grpc.ManagedChannel;

import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.hellos.Client;
import io.grpc.hellos.Coordinator;
import io.grpc.hellos.CoordinatorServiceGrpc;
import io.grpc.hellos.fleetClientGrpc;
import se.oru.coordination.coordination_oru.*;
import se.oru.coordination.coordination_oru.simulation2D.TrajectoryEnvelopeCoordinatorSimulation;
import Visualizer.util.BrowserVisualization;
import Visualizer.util.Missions;


public class FleetClient {

    private static final Logger logger = Logger.getLogger(FleetClient.class.getName());
    private final fleetClientGrpc.fleetClientBlockingStub blockingStub;
    private final CoordinatorServiceGrpc.CoordinatorServiceBlockingStub blockingStub2;

    // object using in cord
    public fleetClient.FleetClient FleetClient;

    public FleetClient(Channel channel) {
        //blockingStub = HelloWorldServiceGrpc.newBlockingStub(channel);
        blockingStub = fleetClientGrpc.newBlockingStub(channel);
        blockingStub2 = CoordinatorServiceGrpc.newBlockingStub(channel);
    }

    public void makeGreeting(String name) {

        Client.requestMessage request = Client.requestMessage.newBuilder().setName(name).build();
        Client.responseMessage response;

        try {
            //System.out.println("making greeting with : " + name);
            response = blockingStub.message(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "Rpc Failed: {0}", e.getStatus());
            return;
        }
        System.out.println("Logging the response of the server ...");
        System.out.println(" Response is: " + response.getName());
    }

    public void makeGreeting2(Pair<String, Integer> pair, String type, String connection, String timeStamp) {

        Client.getRobotID getrobotid = Client.getRobotID.newBuilder().setKey(pair.getFirst()).setValue(pair.getSecond()).setType(type).setConnection(connection).setTimeStamp(timeStamp).build();
        Client.robotID robotid;


        try {
            //System.out.println("making greeting with: " + pair);
            robotid = blockingStub.grobotID(getrobotid);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "Rpc Failed: {0}", e.getStatus());
            return;
        }
        System.out.println("Logging the response of the server ...");
    }

    /*public Integer requestCriticalPoint (Pair<Integer, Integer> critcalPoint) {

        Coordinator.getCriticalPoint getcriticalpoint = Coordinator.getCriticalPoint.newBuilder().setRobotID(critcalPoint.getFirst()).setCriticalPoint(critcalPoint.getSecond()).build();
        Coordinator.getCriticalPointResponseMessage robotid = null;

        try {
            //System.out.println("making greeting with: " + pair);
            robotid = blockingStub2.gCriticalPoint(getcriticalpoint);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "Rpc Failed: {0}", e.getStatus());
            //return;
        }
        System.out.println("Logging the response of the server ...");

        return robotid.getCriticalPoint();
    }*/

    public void makeRobotReport(String my_robotReport, int robotid, double x, double y, double z, double roll, double pitch, double yaw, double velocity, int pathIndex, double distanceTraveled, int criticalPoint) {
        Client.getRobotReport getRR = Client.getRobotReport.newBuilder().setKan(my_robotReport).setRobotID(robotid).setX(x).setY(y).setZ(z).setRoll(roll).setPitch(pitch).setYaw(yaw).setVelocity(velocity).setPathIndex(pathIndex).setDistanceTraveled(distanceTraveled).setCriticalPoint(criticalPoint).build();
        Client.robotReportResponse robotRsp;


        try {
            System.out.println("sending robotReport pathindex " + pathIndex);
            robotRsp = blockingStub.grobotReport(getRR);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "Rpc Failed: {0}", e.getStatus());
            return;
        }
        System.out.println("Logging the response of the server ...");

    }


    public static void test() {
        double MAX_ACCEL = 1.0;
        double MAX_VEL = 4.0;
        //Instantiate a trajectory envelope coordinator.
        //The TrajectoryEnvelopeCoordinatorSimulation implementation provides
        // -- the factory method getNewTracker() which returns a trajectory envelope tracker
        // -- the getCurrentTimeInMillis() method, which is used by the coordinator to keep time
        //You still need to add one or more comparators to determine robot orderings thru critical sections (comparators are evaluated in the order in which they are added)
        final TrajectoryEnvelopeCoordinatorSimulation tec = new TrajectoryEnvelopeCoordinatorSimulation(MAX_VEL, MAX_ACCEL);
        tec.addComparator(new Comparator<RobotAtCriticalSection>() {
            @Override
            public int compare(RobotAtCriticalSection o1, RobotAtCriticalSection o2) {
                CriticalSection cs = o1.getCriticalSection();
                RobotReport robotReport1 = o1.getRobotReport();
                RobotReport robotReport2 = o2.getRobotReport();
                return ((cs.getTe1Start() - robotReport1.getPathIndex()) - (cs.getTe2Start() - robotReport2.getPathIndex()));
            }
        });
        tec.addComparator(new Comparator<RobotAtCriticalSection>() {
            @Override
            public int compare(RobotAtCriticalSection o1, RobotAtCriticalSection o2) {
                return (o2.getRobotReport().getRobotID() - o1.getRobotReport().getRobotID());
            }
        });

        //You probably also want to provide a non-trivial forward model
        //(the default assumes that robots can always stop)
        tec.setForwardModel(1, new ConstantAccelerationForwardModel(MAX_ACCEL, MAX_VEL, tec.getTemporalResolution(), tec.getControlPeriod(), tec.getRobotTrackingPeriodInMillis(1)));
        tec.setForwardModel(2, new ConstantAccelerationForwardModel(MAX_ACCEL, MAX_VEL, tec.getTemporalResolution(), tec.getControlPeriod(), tec.getRobotTrackingPeriodInMillis(2)));
        //tec.setForwardModel(3, new ConstantAccelerationForwardModel(MAX_ACCEL, MAX_VEL, tec.getTemporalResolution(), tec.getControlPeriod(), tec.getRobotTrackingPeriodInMillis(3)));

        //Need to setup infrastructure that maintains the representation
        tec.setupSolver(0, 100000000);
        //Start the thread that checks and enforces dependencies at every clock tick
        tec.startInference();

        //Setup a simple GUI (null means empty map, otherwise provide yaml file)
        //JTSDrawingPanelVisualization viz = new JTSDrawingPanelVisualization();
        //RVizVisualization.writeRVizConfigFile(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26);
        //RVizVisualization viz = new RVizVisualization();
        BrowserVisualization viz = new BrowserVisualization();
        viz.setInitialTransform(19, 56.5, 35.17);
        tec.setVisualization(viz);

        //Example of how you can add extra info Strings to the visualization of robot status
        TrackingCallback cb = new TrackingCallback(null) {

            @Override
            public void onTrackingStart() {
            }

            @Override
            public void onTrackingFinished() {
            }

            @Override
            public String[] onPositionUpdate() {
                return new String[]{"a", "b", "c"};
            }

            @Override
            public void onNewGroundEnvelope() {
            }

            @Override
            public void beforeTrackingStart() {
            }

            @Override
            public void beforeTrackingFinished() {
            }
        };
        tec.addTrackingCallback(1, cb);
        tec.addTrackingCallback(2, cb);
        tec.addTrackingCallback(3, cb);

        //Load data file with locations and pointers to files containing paths between locations
        Missions.loadLocationAndPathData("paths/test_poses_and_path_data.txt");

        //Place robots in their initial locations (looked up in the data file that was loaded above)
        // -- creates a trajectory envelope for each location, representing the fact that the robot is parked
        // -- each trajectory envelope has a path of one pose (the pose of the location)
        // -- each trajectory envelope is the footprint of the corresponding robot in that pose
        tec.placeRobot(1, Missions.getLocation("r1p"));
        tec.placeRobot(2, Missions.getLocation("r2p"));
        tec.placeRobot(3, Missions.getLocation("r3p"));

        //Make a mission for each robot, and store it in the global hashmap:
        // -- from parking location of robot i (rip)
        // -- to destination location of robot i (desti)
        Missions.enqueueMission(new Mission(1, Missions.getShortestPath("r1p", "dest1")));
        Missions.enqueueMission(new Mission(2, Missions.getShortestPath("r2p", "dest2")));
        Missions.enqueueMission(new Mission(3, Missions.getShortestPath("r3p", "dest3")));

        //Make another mission for each robot, and store it in the global hashmap:
        // -- from destination location of robot i (desti)
        // -- to parking location of robot i (rip)
        Missions.enqueueMission(new Mission(1, Missions.getShortestPath("dest1", "r1p")));
        Missions.enqueueMission(new Mission(2, Missions.getShortestPath("dest2", "r2p")));
        Missions.enqueueMission(new Mission(3, Missions.getShortestPath("dest3", "r3p")));

        System.out.println("Added missions " + Missions.getMissions());


        //Start a mission dispatching thread for each robot, which will run forever
        for (int i = 1; i <= 3; i++) {
            final int robotID = i;
            //For each robot, create a thread that dispatches the "next" mission when the robot is free
            Thread t = new Thread() {
                int iteration = 0;

                @Override
                public void run() {
                    while (true) {


                        //Mission to dispatch alternates between (rip -> desti) and (desti -> rip)
                        Mission m = Missions.getMission(robotID, iteration % 2);
                        synchronized (tec) {
                            //addMission returns true iff the robot was free to accept a new mission
                            if (tec.addMissions(m)) iteration++;
                        }

                        //String messageToSend;
                        //String target = "localhost:50051";

                        //ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();


                        //Sleep for a little (2 sec)
                        //try {
                        //Thread.sleep(2000);

                        ///////////////////////////////////////////////////////////
                        //Pair<String, Integer> pair2 = new Pair<String, Integer>("My ID", robotID);

//                            try {
//                                FleetClient client = new FleetClient(channel);
//                                client.makeGreeting2(pair2);
//
//
//                                client.makeRobotReport("my RobotReport", tec.getRobotReport(robotID).getRobotID()
//                                        , tec.getRobotReport(robotID).getPose().getX(), tec.getRobotReport(robotID).getPose().getY(), tec.getRobotReport(robotID).getPose().getZ(), tec.getRobotReport(robotID).getPose().getRoll()
//                                        , tec.getRobotReport(robotID).getPose().getPitch(), tec.getRobotReport(robotID).getPose().getYaw(), tec.getRobotReport(robotID).getVelocity()
//                                        , tec.getRobotReport(robotID).getPathIndex(), tec.getRobotReport(robotID).getDistanceTraveled(), tec.getRobotReport(robotID).getCriticalPoint());
//                            } finally {
//
//                            }
//
//
//                            /////////////////////////////////////////////////////////////
//
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                   }
//                }
//            };

                        //Start the thread!
                        //t.start();

                    }
                }
            };
        }
    }

    public static void main(String[] args) throws InterruptedException {

        String messageToSend;
        String target = "localhost:50051";


        ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();

        try {
            //FleetClient client = new FleetClient(channel);
            //client.makeGreeting3((messageToSend));

            //test();

        }catch (StatusRuntimeException e) {
                System.out.println("Could not connect");
                return;

        } finally {

            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
