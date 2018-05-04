package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import junit.runner.Version;

public class SimpleDynamoProvider extends ContentProvider {

	//variable needed
	private ArrayList<Node> Nodes;
	private Node self;
	static final String TAG = "SDActivity";
	//The storage to store data
	SharedPreferences data;
	SharedPreferences fail_recovery;
	SharedPreferences back_up;
	private boolean nofail=true;
	private static LinkedList<Socket> Pendings=new LinkedList<Socket>();
	private static LinkedList<Socket> failPendings=new LinkedList<Socket>();
	private int selfnum=0;
	//all flag
	private final int Q=0;
	private final int I=1;
	private final int IM=2;
	private final int IT=3;
	private final int D=4;
	private final int DM=5;
	private final int DT=6;
	private final int OK=7;
	private final int R=8;
	private final int FI=9;
	private final int FIM=10;
	private final int FIT=11;
	private final int FD=12;
	private final int FDM=13;
	private final int FDT=14;
	private final int FOK=15;
	private final int RT=16;
	private final int RM=17;
	private final int RH=18;
	private final int F=19;
	private final int FR=20;
	private final int FB=21;
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		Node successor=findSuccessor(selection,Nodes);
		Log.d(TAG, "delete: "+selection+" to emulator-"+successor.getEmulator()+" begin");
		if(!successor.IDfailed())
		{
			Object reply=Send_Receive(new Request(selection,null,D),
					Integer.parseInt(successor.getEmulator())*2);
			//Log.d(TAG, "delete: "+selection+" finished");
			if(!(reply instanceof Reply))
			{
				reply=Send_Receive(new Request(null,null,F),Integer.parseInt(successor.getEmulator())*2);
				if(reply instanceof Reply)
				{
					Send_Receive(new Request(selection,null,D),Integer.parseInt(successor.getEmulator())*2);
				}else
				{
					successor.setIDfailed(true);
					reply=Send_Receive(new Request(selection,null,FB),
							Integer.parseInt(successor.getReplica1().getEmulator())*2);
					reply=Send_Receive(new Request(selection,null,FD),
							Integer.parseInt(successor.getReplica1().getEmulator())*2);
					if(reply instanceof Reply&&!((Reply) reply).Success)
					{
						successor.setIDfailed(false);
						Send_Receive(new Request(selection,null,D),
								Integer.parseInt(successor.getEmulator())*2);
					}
				}
			}
			if(((Reply)reply).Success)
			{
				Log.d(TAG, "delete: "+selection+" finished\n");
			}
		}
		else
		{
			//handle failure here
			Object reply=Send_Receive(new Request(selection,null,FD),
					Integer.parseInt(successor.getReplica1().getEmulator())*2);
			if(reply instanceof Reply&&!((Reply) reply).Success)
			{
				successor.setIDfailed(false);
				Send_Receive(new Request(selection,null,D),
						Integer.parseInt(successor.getEmulator())*2);
			}
			if(reply instanceof Reply&&((Reply) reply).Success)
			{
				Log.d(TAG, "delete: "+selection+" finished");
			}
		}
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		String key=values.getAsString("key");
		String value=values.getAsString("value");
		Node successor=findSuccessor(key,Nodes);
		if(!successor.IDfailed())
		{
			//Log.d(TAG, "insert: "+key+" to "+successor.getEmulator()+" begin without failure");
			Object reply=Send_Receive(new Request(key,value,I),
					Integer.parseInt(successor.getEmulator())*2);
			if(!(reply instanceof Reply))
			{
				//Log.d(TAG, "insert: failure happened------------------------------------------------------------------");
				reply=Send_Receive(new Request(null,null,F),
						Integer.parseInt(successor.getEmulator())*2);
				if(reply instanceof Reply)
				{
					//Log.d(TAG, "insert: head not fail resend");
					reply=Send_Receive(new Request(key,value,I),
							Integer.parseInt(successor.getEmulator())*2);
				}else
				{
					//Log.d(TAG, "insert: head failed try the next");
					reply=Send_Receive(new Request(null,null,FB),
							Integer.parseInt(successor.getReplica1().getEmulator())*2);
					//transfer the data
					if(reply instanceof Reply&&((Reply) reply).Success) {
						successor.setIDfailed(true);
						reply = Send_Receive(new Request(key, value, FI),
								Integer.parseInt(successor.getReplica1().getEmulator()) * 2);
						if (reply instanceof Reply && !((Reply) reply).Success) {
							successor.setIDfailed(false);
							reply = Send_Receive(new Request(key, value, I),
									Integer.parseInt(successor.getEmulator()) * 2);
							//Log.d(TAG, "insert: detect recovery");
						}
					}else
					{
						 reply=Send_Receive(new Request(key,value,I),
								Integer.parseInt(successor.getEmulator())*2);
					}
				}
			}
			if(((Reply)reply).Success)
			{
				//Log.d(TAG, "insert: "+key+" finished\n");
			}
		}
		else
		{
			//Log.d(TAG, "insert: "+key+" to "+successor.getEmulator()+" with failure");
			//handle failure
			Object reply=Send_Receive(new Request(key,value,FI),
					Integer.parseInt(successor.getReplica1().getEmulator())*2);
			if(reply instanceof Reply&&!((Reply) reply).Success)
			{
				successor.setIDfailed(false);
				reply=Send_Receive(new Request(key,value,I),
						Integer.parseInt(successor.getEmulator())*2);
			}
			if(reply instanceof Reply&&((Reply) reply).Success)
			{
				//Log.d(TAG, "insert: "+key+" finished");
			}
		}
		return null;
	}


	void InsertDynamo(Node successor,String key,String value)
	{
		if(!successor.IDfailed())
		{
			Log.d(TAG, "insert: "+key+" to "+successor.getEmulator()+" begin without failure");
			Object reply=Send_Receive(new Request(key,value,I),
					Integer.parseInt(successor.getEmulator())*2);
			if(!(reply instanceof Reply))
			{
				Log.d(TAG, "insert: failure happened------------------------------------------------------------------");
				reply=Send_Receive(new Request(null,null,F),
						Integer.parseInt(successor.getEmulator())*2);
				if(reply instanceof Reply)
				{
					Log.d(TAG, "insert: head not fail resend");
					reply=Send_Receive(new Request(key,value,I),
							Integer.parseInt(successor.getEmulator())*2);
				}else
				{
					Log.d(TAG, "insert: head failed try the next");
					reply=Send_Receive(new Request(null,null,FB),
							Integer.parseInt(successor.getReplica1().getEmulator())*2);
					//transfer the data
					if((reply instanceof Reply)&&((Reply) reply).Success==true)
					{
						successor.setIDfailed(true);
					}else
					{
						InsertDynamo(successor,key,value);
					}
				}
			}
			if(((Reply)reply).Success)
			{
				Log.d(TAG, "insert: "+key+" finished\n");
			}
		}
		else
		{
			Log.d(TAG, "insert: "+key+" to "+successor.getEmulator()+" with failure");
			//handle failure
			Object reply=Send_Receive(new Request(key,value,FI),
					Integer.parseInt(successor.getReplica1().getEmulator())*2);
			if(reply instanceof Reply&&!((Reply) reply).Success)
			{
				successor.setIDfailed(false);
				InsertDynamo(successor,key,value);
			}
			if(reply instanceof Reply&&((Reply) reply).Success)
			{
				Log.d(TAG, "insert: "+key+" finished");
			}
		}
	}
	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub
		//test
		TelephonyManager tel = (TelephonyManager) this.getContext().getSystemService(Context.TELEPHONY_SERVICE);
		String myPortString = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		Nodes=new ArrayList<Node>();
		data=getContext().getSharedPreferences("data",0);
		fail_recovery=getContext().getSharedPreferences("fail",0);
		back_up=getContext().getSharedPreferences("backup",0);
		nofail=true;
		//do some initialization;
		for(int i=5554;i<=5562;i=i+2)
		{
			Nodes.add(new Node(Integer.toString(i)));
		}
		selfnum=0;
		Collections.sort(Nodes);
		for(Node i:Nodes)
		{
			if(i.getEmulator().compareTo(myPortString)==0)
			{
				self=i;
				break;
			}
			selfnum++;
		}
		int i;
		for(i=0;i<Nodes.size()-2;i++)
		{
			Nodes.get(i).setReplica(Nodes.get(i+1),Nodes.get(i+2));
			Nodes.get(i+2).setCoordinator(Nodes.get(i));
		}

		//set replica of last two Nodes;
		Nodes.get(i).setReplica(Nodes.get(i+1),Nodes.get(0));
		Nodes.get(0).setCoordinator(Nodes.get(i));
		Nodes.get(i+1).setReplica(Nodes.get(0),Nodes.get(1));
		Nodes.get(1).setCoordinator(Nodes.get(i+1));
		try {
			ServerSocket serverSocket = new ServerSocket(10000);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
		} catch (IOException e) {
			Log.e(TAG, "Can't create a ServerSocket");
			//e.printStackTrace();
			return false;
		}
		/*Log.d(TAG, "onCreate: finished ---------------------------------------------------------------------");
		Log.d(TAG, "onCreate: Self is "+self.getEmulator());*/
		return false;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

		String[] columns = new String[] { "key", "value" };
		MatrixCursor result = new MatrixCursor(columns);
		if(selection.equals("@")) {
			Map<String,String> all=(Map<String, String>) data.getAll();
			for(Map.Entry<String,String> E:all.entrySet())
			{
				result.addRow(new Object[]{E.getKey(),E.getValue()});
				//Log.d(TAG, "doInBackground: have "+E.getKey());
			}
		}
		else if(selection.equals("*")){
			for(Node N:Nodes)
			{
				if(!(N.equals(self))) {
					//ArrayList<Reply> replies = (ArrayList<Reply>) queryDynamo(N.coordinator, selection);
					ArrayList<Reply> replies = (ArrayList<Reply>)Send_Receive(new Request(selection,null,Q),
							Integer.parseInt(N.getEmulator())*2);
					//Log.d(TAG, "query: "+selection+" finished");
					if(replies!=null) {
						for (Reply R : replies) {
							result.addRow(new Object[]{R.Key, R.Value});
						}
					}
				}else
				{
					Map<String,String> all=(Map<String, String>) data.getAll();
					for(Map.Entry<String,String> E:all.entrySet())
					{
						result.addRow(new Object[]{E.getKey(),E.getValue()});
						//Log.d(TAG, "doInBackground: have "+E.getKey());
					}
				}
			}
		}
		else {
			Node successor=findSuccessor(selection,Nodes);
			Reply reply=(Reply)queryDynamo(successor,selection);
			if(reply.Value==null) {
				Log.d(TAG, "query: " + selection +"/"+ reply.Key+"/"+reply.Value+"/"+reply.Success+" finished, get null!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" + reply.Value);
			}
			result.addRow(new Object[]{reply.Key,reply.Value});
		}
		return result;
	}

	Object queryDynamo(Node successor,String selection)
	{
		Object result=null;
		if(!successor.replica2.Qfailed()) {
			Log.d(TAG, "queryDynamo: send to "+successor.getReplica2()+ ",the third of "+successor.getEmulator());
			Object reply = Send_Receive(new Request(selection, null, Q),
					Integer.parseInt(successor.getReplica2().getEmulator()) * 2);
			if (!(reply instanceof Object)) {
				Log.d(TAG, "queryDynamo: detect failure send to second");
				successor.replica2.setQfailed(true);
				result=Send_Receive(new Request(selection,null,Q),
						Integer.parseInt(successor.getReplica1().getEmulator())*2);
				if(result==null)
				{
					successor.replica2.setQfailed(false);
					result=queryDynamo(successor,selection);
				}
			}else
			{
				result=reply;
			}
		}
		else
		{
			Log.d(TAG, "queryDynamo: send to second "+successor.getReplica2().getEmulator());
			result=Send_Receive(new Request(selection,null,Q),
					Integer.parseInt(successor.getReplica1().getEmulator())*2);
			if(result==null)
			{
				successor.replica2.setQfailed(false);
				result=queryDynamo(successor,selection);
			}
		}
		if(result==null)
		{
			Log.d(TAG, "queryDynamo: failure happened,get null!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");

		}
		return result;
	}
	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

    static public String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    //function to locally routing
	static public Node findSuccessor(String key,ArrayList<Node> Nodes)
	{
		String hash=null;
		try {
			 hash = genHash(key);
		}catch (NoSuchAlgorithmException e)
		{
			e.printStackTrace();
		}
		for(Node i:Nodes)
		{
			if(i.getHash().compareTo(hash)>=0)
			{
				//Log.d(TAG, "findSuccessor: "+hash+" < "+i.getHash());
				return i;
			}
		}
		//Log.d(TAG, "findSuccessor: "+hash+" < "+Nodes.get(0).getHash());
		return Nodes.get(0);
	}

    //need added class to store all member information of group

	//represent a single Node
    static class Node implements Comparable<Node>
	{
		private String Hash;
		private String Emulator;
		private Node replica1;
		private Node replica2;
		private AtomicBoolean failed;
		private Node coordinator;
		private AtomicBoolean IDfailed;
		private AtomicBoolean Qfailed;
		public Node(String Emulator)
		{
			this.Emulator=Emulator;
			try {
				this.Hash = genHash(Emulator);
			}catch (NoSuchAlgorithmException e)
			{
				e.printStackTrace();
			}
			failed=new AtomicBoolean(false);
			IDfailed=new AtomicBoolean(false);
			Qfailed=new AtomicBoolean(false);
		}

		public String getHash()
		{
			return this.Hash;
		}

		public String getEmulator()
		{
			return this.Emulator;
		}

		public void setReplica(Node replica1,Node replica2)
		{
			this.replica1=replica1;
			this.replica2=replica2;
		}

		public void setCoordinator(Node coordinator)
		{
			this.coordinator=coordinator;
		}
		public Node getCoordinator()
		{
			return this.coordinator;
		}
		public Node getReplica1()
		{
			return replica1;
		}

		public Node getReplica2()
		{
			return replica2;
		}

		public boolean isFailed()
		{
			return failed.get();
		}
		public void setFailed(boolean failed)
		{
			this.failed.compareAndSet(!failed,failed);
		}

		public void setIDfailed(boolean failed)
		{
			this.IDfailed.compareAndSet(!failed,failed);
		}

		public boolean IDfailed()
		{
			return IDfailed.get();
		}

		public void setQfailed(boolean failed)
		{
			this.Qfailed.compareAndSet(!failed,failed);
		}

		public boolean Qfailed()
		{
			return Qfailed.get();
		}
		@Override
		public int compareTo(Node another) {
			return this.Hash.compareTo(another.Hash);
		}
	}

	private static class Request implements Serializable
	{
		public String Key=null;
		public String Value=null;
		public int Flag=0;
		public Request(String key,String Value,int Flag)
		{
			this.Key=key;
			this.Value=Value;
			this.Flag=Flag;
		}

	}

	private static class Reply implements Serializable
	{
		public String Key=null;
		public String Value=null;
		public boolean Success=false;
		public Reply(String key,String Value,boolean Success)
		{
			this.Key=key;
			this.Value=Value;
			this.Success=Success;
		}

	}

	private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

		@Override
		protected Void doInBackground(ServerSocket... sockets) {
			ServerSocket serverSocket = sockets[0];
			SharedPreferences.Editor editor=data.edit();
			SharedPreferences.Editor Backup=back_up.edit();
			if(fail_recovery.contains("fail"))
			{
				Log.d(TAG, "doInBackground: Recover begin ~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
				ArrayList<Reply> data=(ArrayList<Reply>) Send_Receive(new Request(null,null,RT),
						Integer.parseInt(self.getCoordinator().getEmulator())*2);
				while(data==null)
				{
					data=(ArrayList<Reply>) Send_Receive(new Request(null,null,RT),
							Integer.parseInt(self.getCoordinator().getEmulator())*2);
				}
				for(Reply E:data)
				{
					if(E.Value.equals(""))
					{
						editor.remove(E.Key);
					}
					else
					{
						editor.putString(E.Key,E.Value);
						Log.d(TAG, "doInBackground: get missing head "+E.Key);
					}
				}
				data=(ArrayList<Reply>) Send_Receive(new Request(null,null,RM),
						Integer.parseInt(self.getCoordinator().getReplica1().getEmulator())*2);
				while(data==null)
				{
					data=(ArrayList<Reply>) Send_Receive(new Request(null,null,RM),
							Integer.parseInt(self.getCoordinator().getReplica1().getEmulator())*2);
				}
				for(Reply E:data)
				{
					if(E.Value.equals(""))
					{
						editor.remove(E.Key);
					}
					else
					{
						editor.putString(E.Key,E.Value);
						Log.d(TAG, "doInBackground: get missing middle "+E.Key);
					}
				}
				data=(ArrayList<Reply>) Send_Receive(new Request(null,null,RH),
						Integer.parseInt(self.getReplica1().getEmulator())*2);
				while(data==null)
				{
					data=(ArrayList<Reply>) Send_Receive(new Request(null,null,RH),
							Integer.parseInt(self.getReplica1().getEmulator())*2);
				}
				for(Reply E:data)
				{
					if(E.Value.equals(""))
					{
						editor.remove(E.Key);
					}
					else
					{
						editor.putString(E.Key,E.Value);
						Log.d(TAG, "doInBackground: get missing tail "+ E.Key);
					}
				}
				Reply p=(Reply) Send_Receive(new Request(Integer.toString(selfnum),null,R),
						Integer.parseInt(self.getReplica2().getEmulator())*2);
				while(p==null)
				{
					p=(Reply) Send_Receive(new Request(Integer.toString(selfnum),null,R),
						Integer.parseInt(self.getReplica2().getEmulator())*2);
				}
				editor.commit();
				Log.d(TAG, "doInBackground: Recovery finish~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
			}else {
				SharedPreferences.Editor temp=fail_recovery.edit();
				temp.putString("fail","");
				temp.commit();
				Log.d(TAG, "doInBackground:First Begin*****************************************************************************");
			}
            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            try {
				while (true) {
					Request msg;
					Socket clientSocket = serverSocket.accept();
					ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
					msg=(Request) in.readObject();
					int flag=msg.Flag;
					String key=msg.Key;
					if(flag==Q)
					{
						ObjectOutputStream out=new ObjectOutputStream(clientSocket.getOutputStream());
						if(!key.equals("*")) {
							//Log.d(TAG, "doInBackground: recieve query");
							out.writeObject(new Reply(key, data.getString(key, null), true));
							out.close();
							clientSocket.close();
						}else {
							//handle *
							ArrayList<Reply> replies=new ArrayList<Reply>();
							Map<String,String> all=(Map<String, String>) data.getAll();
							for(Map.Entry<String,String> E:all.entrySet())
							{
								replies.add(new Reply(E.getKey(),E.getValue(),true));
								//Log.d(TAG, "doInBackground: have "+E.getKey());
							}
							out.writeObject(replies);
						}
					}
					else if(flag==I)
					{
						if(!self.getReplica1().isFailed()&&!self.getReplica2().isFailed()) {
							//Log.d(TAG, "doInBackground: Insert head " + key);
							//Log.d(TAG, "doInBackground: The size of Pending queue before adding is " + Pendings.size());
							editor.putString(key, msg.Value);
							editor.commit();
							Send(new Request(key, msg.Value, IM),
									Integer.parseInt(self.getReplica1().getEmulator()) * 2);
							Pendings.add(clientSocket);
							Log.d(TAG, "doInBackground: insert normal "+key);
						}
						else if(self.getReplica1().isFailed())
						{
							editor.putString(key, msg.Value);
							editor.commit();
							Send(new Request(key, msg.Value, IT),
									Integer.parseInt(self.getReplica2().getEmulator()) * 2);
							Pendings.add(clientSocket);
							Backup.putString(key,msg.Value);
							Log.d(TAG, "doInBackground: insert with middle fail "+key);
						}
						else
						{
							editor.putString(key, msg.Value);
							editor.commit();
							Send(new Request(key, msg.Value, FIM),
									Integer.parseInt(self.getReplica1().getEmulator()) * 2);
							Pendings.add(clientSocket);
							Backup.putString(key,msg.Value);
							Log.d(TAG, "doInBackground: insert with tail fail "+key);
						}
						//Log.d(TAG, "doInBackground: Insert head "+key);
					}
					else if(flag==IM)
					{
						//Log.d(TAG, "doInBackground: Insert middle "+key);
						editor.putString(key,msg.Value);
						editor.commit();
						Send(new Request(key,msg.Value,IT),
								Integer.parseInt(self.getReplica1().getEmulator())*2);
						Log.d(TAG, "doInBackground: insert middle normally "+key);
						//Log.d(TAG, "doInBackground: Insert middle "+key);
					}
					else if(flag==IT)
					{
						//Log.d(TAG, "doInBackground: Insert tail "+key);
						editor.putString(key,msg.Value);
						editor.commit();
						Send(new Request(key,null,OK),
								Integer.parseInt(self.getCoordinator().getEmulator())*2);
						//Log.d(TAG, "doInBackground: insert tail "+key);
					}
					else if(flag==OK)
					{
						Socket toReply=Pendings.poll();
						ObjectOutputStream out=new ObjectOutputStream(toReply.getOutputStream());
						out.writeObject(new Reply(null,null,true));
						out.close();
						toReply.close();
						Log.d(TAG, "doInBackground: receive Delete OK the size of Pending is "+Pendings.size());
					}
					else if(flag==D)
					{
						if(!self.getReplica1().isFailed()&&!self.getReplica2().isFailed()) {
							Log.d(TAG, "doInBackground: receive delete normal "+key);
							editor.remove(key);
							editor.commit();
							Send(new Request(key, null, DM),
									Integer.parseInt(self.getReplica1().getEmulator()) * 2);
							Pendings.add(clientSocket);
						}else if(self.getReplica1().isFailed())
						{
							Log.d(TAG, "doInBackground: recieve delete fail "+key);
							editor.remove(key);
							editor.commit();
							Send(new Request(key, null, DT),
									Integer.parseInt(self.getReplica2().getEmulator()) * 2);
							Pendings.add(clientSocket);
							Backup.putString(key,"");
						}
						else
						{
							Log.d(TAG, "doInBackground: recieve delete fail "+key);
							editor.remove(key);
							editor.commit();
							Send(new Request(key, null, FDM),
									Integer.parseInt(self.getReplica1().getEmulator()) * 2);
							Pendings.add(clientSocket);
							Backup.putString(key,"");
						}
						Log.d(TAG, "doInBackground: The size of Pending "+Pendings.size());
					}
					else if(flag==DM){
						editor.remove(key);
						editor.commit();
						Send(new Request(key,null,DT),
								Integer.parseInt(self.getReplica1().getEmulator())*2);
					}
					else if(flag==DT)
					{
						editor.remove(key);
						editor.commit();
						Send(new Request(null,null,OK),
								Integer.parseInt(self.getCoordinator().getEmulator())*2);
					}
					else if (flag==F)
					{
						if(nofail) {
							for(Socket S:Pendings)
							{
								S.close();
							}
							Pendings.clear();
							Object reply = Send_Receive(new Request(null, null, FR),
									Integer.parseInt(self.getReplica1().getEmulator()) * 2);
							if (!(reply instanceof Reply)) {
								self.getReplica1().setFailed(true);
								nofail=false;
								Log.d(TAG, "doInBackground: fail detected "+self.getReplica1().getEmulator());
							} else {
								reply = Send_Receive(new Request(null, null, FR),
										Integer.parseInt(self.getReplica2().getEmulator()) * 2);
								if(!(reply instanceof Reply)) {
									self.getReplica2().setFailed(true);
									nofail=false;
									Log.d(TAG, "doInBackground: fail detected " + self.getReplica2().getEmulator());
								}
							}
							ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
							out.writeObject(new Reply(null, null, true));
							out.close();
							clientSocket.close();
						}
						else{
							ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
							out.writeObject(new Reply(null, null, true));
							out.close();
							clientSocket.close();
						}
					}
					else if(flag==FR)
					{
						ObjectOutputStream out=new ObjectOutputStream(clientSocket.getOutputStream());
						out.writeObject(new Reply(null,null,true));
						out.close();
						clientSocket.close();
					}
					else if(flag==FI)
					{
						if(!nofail) {
							//Log.d(TAG, "doInBackground: recieve key "+key+" fail happened");
							editor.putString(key, msg.Value);
							editor.commit();
							Backup.putString(key,msg.Value);
							Send(new Request(key, msg.Value, FIT),
									Integer.parseInt(self.getReplica1().getEmulator()) * 2);
							failPendings.add(clientSocket);
						}else
						{
							//Log.d(TAG, "doInBackground: ");
							ObjectOutputStream out=new ObjectOutputStream(clientSocket.getOutputStream());
							out.writeObject(new Reply(null,null,false));
							out.close();
							clientSocket.close();
						}
						//Log.d(TAG, "doInBackground: rescieve fail insert head "+key);
					}
					else if(flag==FIM)
					{
						//this is the message of tail node failure
						editor.putString(key,msg.Value);
						editor.commit();
						Send(new Request(null,null,OK),
								Integer.parseInt(self.getCoordinator().getReplica1().getEmulator())*2);
						//Log.d(TAG, "doInBackground: insert middle with tail fail "+key);
					}
					else if(flag==FIT)
					{
						//Log.d(TAG, "doInBackground: recieve fail insert tail "+key);
						editor.putString(key,msg.Value);
						editor.commit();
						Send(new Request(null,null,FOK),
								Integer.parseInt(self.getCoordinator().getReplica1().getEmulator())*2);
					}
					else if(flag==FD)
					{
						if(!nofail) {
							editor.remove(key);
							editor.commit();
							Backup.putString(key,"");
							Send(new Request(key, msg.Value, FDT),
									Integer.parseInt(self.getReplica1().getEmulator()) * 2);
							failPendings.add(clientSocket);
						}else
						{
							ObjectOutputStream out=new ObjectOutputStream(clientSocket.getOutputStream());
							out.writeObject(new Reply(null,null,false));
							out.close();
							clientSocket.close();
						}
					}
					else if(flag==FDM)
					{
						//handle tail node fail
						editor.remove(key);
						editor.commit();
						Send(new Request(null,null,OK),
								Integer.parseInt(self.getCoordinator().getReplica1().getEmulator())*2);
					}
					else if(flag==FDT)
					{
						editor.remove(key);
						editor.commit();
						Send(new Request(null,null,FOK),
								Integer.parseInt(self.getCoordinator().getReplica1().getEmulator())*2);
					}
					else if(flag==FOK)
					{
						Socket socket=failPendings.poll();
						ObjectOutputStream out=new ObjectOutputStream(socket.getOutputStream());
						out.writeObject(new Reply(null,null,true));
						out.close();
						socket.close();
					}
					else if(flag==RH)
					{
						Log.d(TAG, "doInBackground: recieve recover head------------------------------------------");
						Backup.commit();
						ArrayList<Reply> replies=new ArrayList<Reply>();
						Map<String,String> data=(Map<String, String>) back_up.getAll();
						for(Map.Entry<String,String> E:data.entrySet())
						{
							replies.add(new Reply(E.getKey(),E.getValue(),true));
						}
						ObjectOutputStream out=new ObjectOutputStream(clientSocket.getOutputStream());
						out.writeObject(replies);
						out.close();
						clientSocket.close();

						Backup.clear();
						Backup.commit();
						nofail=true;
						self.getCoordinator().getReplica1().setIDfailed(false);
						self.getCoordinator().getReplica1().setQfailed(false);
					}
					else if(flag==RM)
					{
						Log.d(TAG, "doInBackground: recieve recover middle-----------------------------------------");
						Log.d(TAG, "doInBackground: the size of normal pending is "+Pendings.size());
						Backup.commit();
						ArrayList<Reply> replies=new ArrayList<Reply>();
						Map<String,String> data=(Map<String, String>) back_up.getAll();
						for(Map.Entry<String,String> E:data.entrySet())
						{
							replies.add(new Reply(E.getKey(),E.getValue(),true));
						}
						ObjectOutputStream out=new ObjectOutputStream(clientSocket.getOutputStream());
						out.writeObject(replies);
						out.close();
						clientSocket.close();

						if(nofail)
						{
							Pendings.clear();
						}else
						{
							nofail=true;
						}
						Backup.clear();
						Backup.commit();
						self.getReplica1().setFailed(false);
						self.getReplica1().setIDfailed(false);
						self.getReplica1().setQfailed(false);
					}
					else if(flag==RT)
					{
						Log.d(TAG, "doInBackground: Recieve recover tail-------------------------------------");
						//Log.d(TAG, "doInBackground: The size of Pending is "+Pendings.size());
						Backup.commit();
						ArrayList<Reply> replies=new ArrayList<Reply>();
						Map<String,String> data=(Map<String, String>) back_up.getAll();
						for(Map.Entry<String,String> E:data.entrySet())
						{
							replies.add(new Reply(E.getKey(),E.getValue(),true));
						}
						ObjectOutputStream out=new ObjectOutputStream(clientSocket.getOutputStream());
						out.writeObject(replies);
						out.close();
						clientSocket.close();

						if(nofail)
						{
							Pendings.clear();
						}else
						{
							nofail=true;
						}
						Backup.clear();
						Backup.commit();
						self.getReplica2().setFailed(false);
						self.getReplica2().setIDfailed(false);
						self.getReplica2().setQfailed(false);
					}
					else if(flag==R)
					{
						Log.d(TAG, "doInBackground: recieve recovery-------------------------------------------------------");
						if(!nofail)
						{
							nofail=true;
						}
						int i=Integer.parseInt(msg.Key);

						Nodes.get(i).setQfailed(false);
						Nodes.get(i).setIDfailed(false);
						ObjectOutputStream out=new ObjectOutputStream(clientSocket.getOutputStream());
						out.writeObject(new Reply(null,null,true));
						out.close();
						clientSocket.close();
					}
					else if(flag==FB)
					{
						if(nofail) {
							Object reply = Send_Receive(new Request(null, null, FR),
									Integer.parseInt(self.getCoordinator().getReplica1().getEmulator()) * 2);
							if (!(reply instanceof Reply)) {
								nofail = false;
								failPendings.clear();
							}
							ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
							if (nofail == false) {
								out.writeObject(new Reply(null, null, true));
							} else {
								out.writeObject(new Reply(null, null, false));
							}
							out.close();
							clientSocket.close();
						}
						else
						{
							ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
							out.writeObject(new Reply(null, null, true));
							out.close();
							clientSocket.close();
						}
					}
				}
			}catch (IOException e)
			{
				Log.e(TAG, "doInBackground: ",e );
			}catch (ClassNotFoundException e)
			{
				Log.e(TAG, "doInBackground: ",e );
			}
			return null;
		}
	}

	public Object Send_Receive(Request m,int port)
	{
		Object result=null;
		try {
			Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), port);
			//socket.setSoTimeout(10000);
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			out.writeObject(m);
			out.flush();
			socket.setSoTimeout(1300);
			ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
			result = in.readObject();
			in.close();
			out.close();
			socket.close();
		} catch (SocketTimeoutException e)
		{
			//Log.d(TAG, "Send_Receive: detect failures "+ e.getClass().getSimpleName());
			return null;
		}catch (Exception e1)
		{
			//Log.d(TAG, "Send_Receive: message "+m.Key+"/"+m.Value+"/"+m.Flag);
			//Log.e(TAG, "Send_Receive: ",e1 );
		}
		return result;
	}
	public void Send(Request m,int port)
	{
		//Log.d(TAG, "Send: Message "+m.Key+"/"+m.Value+"/"+m.Flag+" to "+port/2);
		try {
			Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), port);
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			out.writeObject(m);
			out.close();
			socket.close();
		}catch (Exception e)
		{
			//Log.d(TAG, "Send: Message failed");
			//Log.e(TAG, "Send: ",e);
		}
	}
}
