package GRPC;

import CoordinatorPackage.RemoteAbstractTrajectoryEnvelopeCoordinator;
import CoordinatorPackage.containers.tecStuff;
import CoordinatorPackage.containers.MakeFootPrint;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.coordinator.Coordinator;
import io.grpc.coordinator.CoordinatorServiceGrpc;
import io.grpc.fleetClients.FleetClientsServiceGrpc;
import io.grpc.fleetClients.Fleetclients;
import io.grpc.stub.StreamObserver;
import org.metacsp.multi.allenInterval.AllenIntervalConstraint;
import org.metacsp.multi.spatioTemporal.paths.Pose;
import org.metacsp.multi.spatioTemporal.paths.PoseSteering;
import se.oru.coordination.coordination_oru.*;


import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class CoordinatorServiceImpl extends CoordinatorServiceGrpc.CoordinatorServiceImplBase {

    private static final Logger logger = Logger.getLogger(CoordinatorServiceImpl.class.getName());
    private final FleetClientsServiceGrpc.FleetClientsServiceBlockingStub blockingStubClient;
    public HashMap<Integer, RobotReport> robotIDtoRobotReport = new HashMap<Integer, RobotReport>();
    public HashMap<Integer, clientConnection> robotIDtoClientConnection = new HashMap<Integer,clientConnection>();


    RemoteAbstractTrajectoryEnvelopeCoordinator tec = null;


    String target = "localhost:50057";
    ManagedChannel fleetServiceChannel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();

    public CoordinatorServiceImpl() {

        blockingStubClient = FleetClientsServiceGrpc.newBlockingStub(fleetServiceChannel);

    }

    public CoordinatorServiceImpl(RemoteAbstractTrajectoryEnvelopeCoordinator tec) {

        this.tec = tec;
        blockingStubClient = FleetClientsServiceGrpc.newBlockingStub(fleetServiceChannel);

    }


    public void coordinatorgetGreeting(Coordinator.robotsGreeting request,
                                       StreamObserver<Coordinator.robotgreetingResponse> responseObserver){

        if(request.getKan().equals("greeting")){

            int _robotID = request.getRobotID();
            String _type= request.getType();
            String _ip= request.getIP();
            int _port= request.getPort();
            Pose _pose = new Pose(request.getRpose().getX(),request.getRpose().getY(),request.getRpose().getZ(),
                                  request.getRpose().getRoll(), request.getRpose().getPitch(),request.getRpose().getYaw());
            String _timeStamp = request.getTimeStamp();
            double _maxAccel = request.getMaxAccel();
            double _maxVel = request.getMaxVel();
            double _trackingPeriod = request.getTrackingPeriodInMillis();
            MakeFootPrint _footprint = new MakeFootPrint(request.getMakeFootPrint().getCenterX(),request.getMakeFootPrint().getCenterY()
                                                        ,request.getMakeFootPrint().getMinVerts(), request.getMakeFootPrint().getMaxVerts(),
                                                        request.getMakeFootPrint().getMinRadius(),request.getMakeFootPrint().getMaxRadius());

            // deserializing poseSteering that comes from the robot
            PoseSteering[] _poseSteering = null;
            try {
                _poseSteering = (PoseSteering[]) convertFromBytes(request.getPoseSteering().toByteArray());
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }


            clientConnection _clientConnection = new clientConnection(_type,_ip,_port,_pose,_timeStamp,_maxAccel,_maxVel,_trackingPeriod,_footprint,_poseSteering);
            robotIDtoClientConnection.put(_robotID, _clientConnection);

            System.out.println("Got ClientConnections timeStamp: " + robotIDtoClientConnection.get(1).getTimeStamp());
            System.out.println("Got ClientConnections type: " + robotIDtoClientConnection.get(1).getType());



            robotgreetingResponse(responseObserver);
        }
    }

    public void robotgreetingResponse(StreamObserver<Coordinator.robotgreetingResponse> responseObserver){

        System.out.println("[CoordinatorServiceImpl] tec.numberOfReplicas is: " + tec.getNumberOfReplicas());
        Coordinator.robotgreetingResponse response = Coordinator.robotgreetingResponse.newBuilder().setName("Response").setNumofReplicas(tec.getNumberOfReplicas()).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void coordinatorrobotreport(Coordinator.requestrobotreport request,
                                       StreamObserver<Coordinator.responserobotreport> responseObserver) {

        int _robotID = 0;

        if (request.getReq().equals("my RobotReport")) {

            /** Piecing together the robotReport to put from the request.. this specific "request"
             * is kind of like an asynch update from each client with RobotReports incoming, no actual real response is given,
             * the necessary data is being sent from the clients
             */
            _robotID = request.getRobotid();
            Pose _pose = new Pose(request.getX(), request.getY(),request.getZ(), request.getRoll(),request.getPitch(),request.getYaw());
            RobotReport rR = new RobotReport(_robotID, _pose, request.getPathIndex(),request.getVelocity(),request.getDistanceTraveled(),request.getCriticalPoint());

            robotIDtoRobotReport.put(_robotID,rR);

        }

        responsetorobotreport(responseObserver);
    }


    public void responsetorobotreport(StreamObserver<Coordinator.responserobotreport> responseObserver){


        Coordinator.responserobotreport response = Coordinator.responserobotreport.newBuilder().setName("Response").build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();

    }

    public void responsetorobotreportwithCriticalpoint(StreamObserver<Coordinator.responserobotreport> responseObserver, int criticalP){

        // skickar tillbaka criticalpoint

    }


    public void coordinatorcriticalpoint(Coordinator.coordinatorGetCriticalPointRequestMessage request,
                                         StreamObserver<Coordinator.coordinatorGetCriticalPointResponseMessage> responseObserver) {

        int criticalPoint = 0;
        int criticalPoint2 = 0;


        if (request.getKan().equals("requestcriticalpoint")) {


            criticalPoint = robotIDtoRobotReport.get(request.getRobotID()).getCriticalPoint();
            System.out.println("[CoordinatorService] responding to criticalpoint1 request with: " + criticalPoint);

            //criticalPoint2 = tec.trackers.get(request.getRobotID()).getCriticalPoint();
            //System.out.println("[CoordinatorService] responding to criticalpoint2 request with: " + criticalPoint2);


            coordinatorcriticalpointresponse(responseObserver, criticalPoint);
        }
    }

    public int coordinatorcriticalpointresponse(StreamObserver<Coordinator.coordinatorGetCriticalPointResponseMessage> responseObserver,
                                                int criticalPoint) {
        Coordinator.coordinatorGetCriticalPointResponseMessage response =
                Coordinator.coordinatorGetCriticalPointResponseMessage.newBuilder().setCriticalPoint(criticalPoint).build();

        System.out.println("[CoordinatorService] Responding to critical point request with criticalPoint response: " + criticalPoint);


        responseObserver.onNext(response);
        responseObserver.onCompleted();

        return criticalPoint;


    }


    public RobotReport makeRobotReportRequest(String myreq, int robotID) {
        Fleetclients.robotReportRequest request = Fleetclients.robotReportRequest.newBuilder().setKan(myreq).setRobotID(robotID).build();

        RobotReport rR = null;
        Fleetclients.robotReportResponse response = null;


        try {
            //TODO stoops before blocking stub (no response)

            response = blockingStubClient.grobotReport(request);


            int _robotID = 0;
            _robotID = request.getRobotID();
            Pose _pose = new Pose(response.getX(), response.getY(),response.getZ(), response.getRoll(),response.getPitch(),response.getYaw());
            rR = new RobotReport(_robotID, _pose, response.getPathIndex(),response.getVelocity(),response.getDistanceTraveled(),response.getCriticalPoint());

            robotIDtoRobotReport.put(_robotID,rR);
        }
        catch(StatusRuntimeException e){
            logger.log(Level.WARNING, "Rpc Failed: {0}", e.getStatus());

        }

        return rR;
    }


    public void coordinatordependencies(Coordinator.getCurrentDependencies request,
                                        StreamObserver<Coordinator.noneResponse> responseObserver){

        HashSet<Dependency> depdes = null;
        byte[] dependenciesBytes = null;

        System.out.println("Inside coordinatordependencies ");

        if(request.getKan().equals("currentDependencies")){

            System.out.println("[CoordinatorServiceImpl] inside the request + " +  request.getKan());
            dependenciesBytes= request.getDepBytes().toByteArray();

            try {
                depdes = (HashSet<Dependency>) convertFromBytes(dependenciesBytes);
                System.out.println("[CoordinatorServiceImpl] after depdes");
            } catch (IOException e) {
                e.printStackTrace();

            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }


            currentDepResponse(responseObserver);
        }

        System.out.println("[CoordinatorServiceImpl] deserialized into (outer): " + depdes);
    }


    public void currentDepResponse(StreamObserver<Coordinator.noneResponse> responseObserver){

        Coordinator.noneResponse response =
                Coordinator.noneResponse.newBuilder().setNone("hej").build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }


    public void coordinatorgetTecStuff(Coordinator.tecrequest request,
                                       StreamObserver<Coordinator.tecresponse> responseObserver){

        tecStuff tes = null;
        /**
         * Works to serialize Solver, Visualization, Dependencies etc..
         * but wont be used I think because we can avoid sending this much data over the network
         * */

        if(request.getKan().equals("RequestTecStuff")){


            tes = new tecStuff(request.getRobotID());
            tes.setCurrentTimeInMill(tec.getCurrentTimeInMillis());
            tes.setTeSolver(tec.getSolver());
            tes.setFleetVisualizer(tec.getVisualization());
            tes.setCurrDependencies(tec.getCurrentDependencies());
            
        }
        byte[] tecStuffSerialized = new byte[0];
        try {
           tecStuffSerialized = convertToBytes(tes);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("[CoordinatorServiceImpl] Size of tes" + tecStuffSerialized.length);

        coordinatorRespondTecStuff(responseObserver, tecStuffSerialized);
    }


    public void coordinatorRespondTecStuff(StreamObserver<Coordinator.tecresponse> responseObserver, byte[] tecStuffSerialized){

        ByteString tecStuffSerializedInByteString = ByteString.copyFrom(tecStuffSerialized);
        Coordinator.tecresponse response = Coordinator.tecresponse.newBuilder().setStringresponse("ResponseTecStuff").setTecStuff(tecStuffSerializedInByteString).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }


    // Handling a time request without string Kan parameter, just responding in this
    public void coordinatorgetCurrentTime(Coordinator.timerequest request,
                                       StreamObserver<Coordinator.timeresponse> responseObserver){

        System.out.println("[CoordinatorServiceImpl] got request of current time, answering with: " + tec.getCurrentTimeInMillis());

        Coordinator.timeresponse response = Coordinator.timeresponse.newBuilder().setCurrentTime(tec.getCurrentTimeInMillis()).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void coordinatorgetAllenInterval(Coordinator.allenInterval message,
                                            StreamObserver<Coordinator.noneResponse> streamObserver){
        AllenIntervalConstraint allenIntervalConstraint = null;
        try {
           allenIntervalConstraint = (AllenIntervalConstraint) convertFromBytes(message.getAllenIntervalBytes().toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        synchronized (tec.getSolver()){
            if(message.getKan().equals("Add")){
                tec.getSolver().addConstraint(allenIntervalConstraint);
            }
            if(message.getKan().equals("Remove")){
                tec.getSolver().removeConstraint(allenIntervalConstraint);
            }
        }
    }


    public void coordinatorgetRobotReportRequest(Coordinator.trackerRobotReportRequest request,
                                                 StreamObserver<Coordinator.requestrobotreport> responseObserver){

        System.out.println("[CoordinatorServiceImpl] Getting RobotReport Request from tracker ");
        if(request.getKan().equals("TrackerRequestRobotReport")){

            RobotReport robotReport = new RobotReport();
            robotReport = tec.getRobotReport(request.getRobotID());
            coordinatorgetRobotReportResponse(responseObserver, robotReport);
        }

    }
public void coordinatorgetRobotReportResponse(StreamObserver<Coordinator.requestrobotreport> responseObserver, RobotReport rr){

        Coordinator.requestrobotreport response = Coordinator.requestrobotreport.newBuilder()
                .setRobotid(rr.getRobotID()).setX(rr.getPose().getX()).setY(rr.getPose().getY()).setZ(rr.getPose().getZ())
                .setRoll(rr.getPose().getRoll()).setPitch(rr.getPose().getPitch()).setYaw(rr.getPose().getYaw())
                .setPathIndex(rr.getPathIndex()).setVelocity(rr.getVelocity()).setDistanceTraveled(rr.getDistanceTraveled())
                .setCriticalPoint(rr.getCriticalPoint()).build();

        System.out.println("[CoordinatorServiceImpl] responding with RobotReport to Tracker ");
        responseObserver.onNext(response);
        responseObserver.onCompleted();
}



    private byte[] convertToBytes(Object object) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(object);
            return bos.toByteArray();
        }
    }


    private Object convertFromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream in = new ObjectInputStream(bis)) {

            return in.readObject();
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {


    }


}