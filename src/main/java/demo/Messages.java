package demo;

class AbortMessage {
    public int ballot;

    public AbortMessage(int ballot){
        this.ballot = ballot;
    }
    
}

class AckMessage {
    public int ballot;

    public AckMessage(int ballot){
        this.ballot = ballot;
    }
    
}

class CrashMessage {
    public CrashMessage(){}
}

class DecideMessage {
    public Integer v;

    public DecideMessage(Integer v){
        this.v = v;
    }
    
}

class GatherMessage {
    public int ballot;
    public int imposeBallot;
    public Integer estimate;

    public GatherMessage(int ballot, int imposeBallot, Integer estimate){
        this.ballot = ballot;
        this.imposeBallot = imposeBallot;
        this.estimate = estimate;
    }
    
}

class HoldMessage {
    public HoldMessage(){}
}

class ImposeMessage {
    public int ballot;
    public Integer proposal;

    public ImposeMessage(int ballot, Integer proposal){
        this.ballot = ballot;
        this.proposal = proposal;
    }
}

class LaunchMessage {
    public LaunchMessage(){}
}

class ProposeMessage {
	public int v;
	
	public ProposeMessage(int v) {
		this.v = v;
	}

}

class ReadMessage {
    public int ballot;

    public ReadMessage(int ballot){
        this.ballot = ballot;
    }
    
}

class StartTimeMessage {
    long time;
    public StartTimeMessage(long time){
        this.time  = time;
    }
}








