package demo;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Pair;

import java.time.Duration;
import java.util.ArrayList;


public class Process extends UntypedAbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final int N; //Number of processes
    private final int id; //id of current process
    private ProcessesRef processes;
    private Integer proposal;
    private int ballot;
    private int readBallot;
    private int imposeBallot;
    private Integer estimate;
    private ArrayList<Pair<Integer,Integer>> states;
    private long startTime = 0;

    private boolean proposingStatus = true;
    private boolean faultProne = false;
    private boolean silent = false;
    private double crashProbability = 0.2;
    private int timeToProposeAgain = 50;
    private boolean hold = false;
    private boolean decided = false;

    private int prevAbortBallot = 0;
    private int prevGatherBallot = 0;
    private int ackCount = 0;
    private int stateEntryNonPostive = 0;

    private boolean fullLog = true; //true: all outputs; false: only decide output


    public Process(int ID, int nb) {
        N = nb;
        id = ID;
        ballot = id - N;
        readBallot = 0;
        imposeBallot = id - N;
        estimate = null;
        proposal = null;
        resetState(N);

        prevAbortBallot = -2 * N;
        prevGatherBallot = -2 * N;

        getContext().system().scheduler().scheduleOnce(Duration.ofMillis(timeToProposeAgain), getSelf(), "Re-propose", getContext().system().dispatcher(), ActorRef.noSender());

    }

    public static Props createActor(int ID, int nb) {
        return Props.create(Process.class, () -> {
            return new Process(ID, nb);
        });
    }
    
    private void resetState(int length){
    	stateEntryNonPostive = 0;
        states = new ArrayList<Pair<Integer,Integer>>();
        for (int i = 0; i < length; i++) {
            states.add(new Pair<Integer,Integer>(null, 0));
          }
    }

    private int countPostiveEntries(ArrayList<Pair<Integer,Integer>> array){
        int count = 0;
        for(Pair<Integer,Integer> pair : array){
            if (pair.second() > 0){
                count++;
            }
        }
        return count;
    }
    
    
    private void proposeReceived(Integer v) {
        proposingStatus = true;
        proposal = v;
        ballot += N;
        resetState(N);
        if (fullLog) {log.info("p"+self().path().name()+" proposed (proposal="+ proposal + ")");}
        for (ActorRef actor : processes.references) {
            actor.tell(new ReadMessage(ballot), this.getSelf());
        }
    }

    private void launchReceived() {
        Integer v = 0;
        if (Math.random()>0.5){
            v = 1;
        }
        proposeReceived(v);
    }
    
    private void readReceived(int newBallot, ActorRef pj) {

        if(readBallot>newBallot || imposeBallot>newBallot){
            //log.info("r: "+getSender().path().name() + " sends ABORT to " + pj.path().name());
            pj.tell(new AbortMessage(newBallot), getSelf());
        } else {
            readBallot = newBallot;
            pj.tell(new GatherMessage(newBallot, imposeBallot, estimate), this.getSelf());
        }
    }

    private void gatherReceived(int newBallot, int estBallot, Integer estimate, ActorRef pj){
        states.set(Integer.valueOf(pj.path().name()), new Pair<Integer,Integer>(estimate, estBallot));
        if(estBallot <= 0) {
        	stateEntryNonPostive++;
        }
        
        int numberOfStateEntries = countPostiveEntries(states) + stateEntryNonPostive;
         
        if (numberOfStateEntries> (N/2) && prevGatherBallot != newBallot){
            prevGatherBallot = newBallot;

            if(countPostiveEntries(states) > 0) {
                int maxBallot = 0;
                for (int i = 0; i < states.size(); i++) {
                    if (states.get(i).second() > maxBallot) {
                        maxBallot = states.get(i).second();
                        proposal = states.get(i).first();
                    }
                }
            }
            
            resetState(N);

            for (ActorRef actor : processes.references) {
                actor.tell(new ImposeMessage(ballot, proposal), this.getSelf());
             }
        }
    }

    private void imposeReceived(int newBallot, Integer v, ActorRef pj){
        if(readBallot > newBallot || imposeBallot > newBallot) {
            pj.tell(new AbortMessage(newBallot), this.getSender());
        } 
        else {
            estimate = v;
            imposeBallot = newBallot;
            pj.tell(new AckMessage(newBallot), this.getSender());
        }
    }

    private void ackReceived(int ballot){
        this.ackCount++;
        if(this.ackCount > (N/2)){
            this.ackCount = 0;
            if(fullLog){
                log.info("p"+self().path().name()+" received ACK from ALL" + " (ballot="+ ballot + ")");
            }
            for(ActorRef actor : processes.references) {
                actor.tell(new DecideMessage(proposal), this.getSelf());
            }
        }
    }

    private void decideReceived(Integer v){
        if (!decided){
            decided = true;
            long elapsedTime =  System.currentTimeMillis() - startTime;
            log.info("p"+self().path().name()+" received DECIDE from p" + getSender().path().name() + " (value="+ v + ")"+ " | time: " + elapsedTime);
            for (ActorRef actor : processes.references) {
                actor.tell(new DecideMessage(proposal), this.getSelf());
            }
            silent = true; //Because of this: If a correct process decides, no correct process aborts infinitely often.
        }
    } 
    
    
    private void abortReceived(int ballot) {
        if(ballot != prevAbortBallot && !decided){
            prevAbortBallot = ballot;
            proposingStatus = false;
        }
    }
    
    
    public void onReceive(Object message) throws Throwable {
        if (faultProne && !silent) {
            double draw = Math.random();
            if (draw < crashProbability) {
                silent = true;
                if (fullLog){
                	log.info("p" + self().path().name() + " crashed - enters silent mode");
                }
            }
        }
        if (!silent) {
            if (message instanceof ProcessesRef) {
            	ProcessesRef m = (ProcessesRef) message;
                processes = m;
                if (fullLog){
                	log.info("p" + self().path().name() + " received references");
                }
            }
            
            else if (message instanceof ProposeMessage) {
                ProposeMessage m = (ProposeMessage) message;
                if (fullLog){
                	log.info("p" + self().path().name() + " received PROPOSE from p" + getSender().path().name() + " (value = " + m.v + ")");
                }
                this.proposeReceived(m.v);
            }
            
            else if (message instanceof ReadMessage) {
                ReadMessage m = (ReadMessage) message;
                if (fullLog){
                	log.info("p" + self().path().name() + " received READ from p" + getSender().path().name() + " (ballot = "+ m.ballot + ")");
                }
                this.readReceived(m.ballot, getSender());
            }
            
            else if (message instanceof AbortMessage) {
                AbortMessage m = (AbortMessage) message;
                if (fullLog){
                	log.info("p" + self().path().name() + " received ABORT from p" + getSender().path().name() + " (ballot = "+ m.ballot + ")");
                }
                this.abortReceived(m.ballot);
            }
            
            else if (message instanceof GatherMessage) {
                GatherMessage m = (GatherMessage) message;
                if (fullLog){
                	log.info("p" + self().path().name() + " received GATHER from p" + getSender().path().name() + " (ballot = "+ m.ballot + ")");
                }
                this.gatherReceived(m.ballot, m.imposeBallot, m.estimate, getSender());
            }
            
            else if (message instanceof ImposeMessage) {
                ImposeMessage m = (ImposeMessage) message;
                if(fullLog) {
                	log.info("p" + self().path().name() + " received IMPOSE from p" + getSender().path().name() + " (ballot = "+ m.ballot + ", prop = "+m.proposal+")");
                }
                this.imposeReceived(m.ballot, m.proposal, getSender());
            }
            
            else if (message instanceof AckMessage) {
                AckMessage m = (AckMessage) message;
                if(fullLog) {
                    log.info("p" + self().path().name() + " received ACK from p" + getSender().path().name() + " (ballot = "+ m.ballot + ")");
                }
                this.ackReceived(m.ballot);
            }
            
            else if (message instanceof DecideMessage) {
                DecideMessage m = (DecideMessage) message;
//                if(fullLog) {
//                    log.info("p"+self().path().name()+" received DECIDE from p" + getSender().path().name() + " (value = "+ m.v + ")");
//                }
                this.decideReceived(m.v);
            }
            
            else if (message instanceof CrashMessage) {
                if(fullLog){
                	log.info("p" + self().path().name() + " received CRASH from p" + getSender().path().name() + " - enters fault-prone mode");
                }
                faultProne = true;
            }
            
            else if (message instanceof LaunchMessage) {
                if(fullLog){
                	log.info("p" + self().path().name() + " received LAUNCH from p" + getSender().path().name());
                }
                launchReceived();
            }
            
            else if (message instanceof HoldMessage) {
                log.info("p" + self().path().name() + " received HOLD from p" + getSender().path().name());
                hold = true;
            }
            
            else if(message instanceof StartTimeMessage){
                startTime = ((StartTimeMessage)message).time;
            }
            
            else if(message instanceof String){
                if((String)message=="Re-propose"){
                    if(!hold && !proposingStatus) {
                    	proposeReceived(proposal);
                    }
                    getContext().system().scheduler().scheduleOnce(Duration.ofMillis(timeToProposeAgain), getSelf(), "Re-propose", getContext().system().dispatcher(), ActorRef.noSender());
                }
            }
        }
        
    }
}
