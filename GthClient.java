import java.io.*;
import java.net.*;

public class GameClient {
    public final static int WHO_NONE = 0;
    public final static int WHO_WHITE = 1;
    public final static int WHO_BLACK = 2;
    public final static int WHO_OTHER = 3;

    public final static int STATE_CONTINUE = 0;
    public final static int STATE_DONE = 1;

    public int winner = WHO_NONE;
    public Move move;

    public boolean time_controls = false;
    public int white_time_control;
    public int black_time_control;
    public int my_time;
    public int opp_time;

    private final static String client_version = "0.9";
    private int who = WHO_NONE;
    private Socket sock = null;
    private BufferedReader fsock_in = null;
    private PrintStream fsock_out = null;
    private final static int server_base = 29057;

    private String msg_txt;
    private int msg_code;
    private int serial = 1;

    private void get_msg()
      throws IOException {
	String buf = fsock_in.readLine();
	int len = buf.length();
	if (len < 4)
	    throw new IOException("short read");
	if (!Character.isDigit(buf.charAt(0)) ||
	    !Character.isDigit(buf.charAt(1)) ||
	    !Character.isDigit(buf.charAt(2)) ||
	    buf.charAt(3) != ' ')
	    throw new IOException("ill-formatted response code");
	msg_txt = buf.substring(4);
	msg_code = Integer.parseInt(buf.substring(0, 3));
    }

    private int opponent(int w) {
	if (w != WHO_WHITE && w != WHO_BLACK)
	    throw new Error("internal error: funny who");
	if (w == WHO_WHITE)
	    return WHO_BLACK;
	return WHO_WHITE;
    }

    private Move parse_move()
      throws IOException {
      StringReader msg_reader = new StringReader(msg_txt);
      StreamTokenizer toks = new StreamTokenizer(msg_reader);

      if (toks.nextToken() != toks.TT_NUMBER)
	throw new IOException("expected serial number");
      if (serial != (int)toks.nval)
	throw new IOException("synchronization lost");
      switch(msg_code) {
      case 312:
      case 314:
      case 323:
      case 324:
      case 326:
	if (toks.nextToken() != toks.TT_WORD || !toks.sval.equals("..."))
	  throw new IOException("expected ellipsis");
      }
      if (toks.nextToken() != toks.TT_WORD)
	throw new IOException("expected move");
      Move m = new Move(toks.sval);
      switch (msg_code) {
      case 313:
      case 314:
	if (toks.nextToken() != toks.TT_NUMBER)
	  throw new IOException("expected time control");
	int whose_move = WHO_BLACK;
	if (msg_code == 314)
	  whose_move = WHO_WHITE;
	if (whose_move == who)
	  my_time = (int)toks.nval;
	else
	  opp_time = (int)toks.nval;
      }
      msg_reader.close();
      return m;
    }

    private void get_time_controls()
      throws IOException
    {
	int i, j;
	for (i = 0; i < msg_txt.length(); i++)
	    if (Character.isDigit(msg_txt.charAt(i)))
		break;
	if (i >= msg_txt.length())
	    throw new IOException("cannot find time controls in message text");
	j = i;
	while(Character.isDigit(msg_txt.charAt(j)))
	    j++;
	white_time_control = Integer.parseInt(msg_txt.substring(i, j));
	i = j;
	while(!Character.isDigit(msg_txt.charAt(i)))
	    i++;
	j = i;
	while(!Character.isDigit(msg_txt.charAt(j)))
	    j++;
	black_time_control = Integer.parseInt(msg_txt.substring(i, j));
    }

    private int get_time()
      throws IOException
    {
	int i, j;
	for (i = 0; i < msg_txt.length(); i++)
	    if (Character.isDigit(msg_txt.charAt(i)))
		break;
	if (i >= msg_txt.length())
	    throw new IOException("cannot find time in message text");
	j = i;
	while(Character.isDigit(msg_txt.charAt(j)))
	    j++;
	return Integer.parseInt(msg_txt.substring(i, j));
    }

    private void close()
      throws IOException {
	if (sock == null)
	    return;
	try {
	    fsock_out.close();
	    fsock_in.close();
	    sock.close();
	} finally {
	    fsock_out = null;
	    fsock_in = null;
	    sock = null;
	}
    }
    
    private String zeropad(int n) {
	if (n > 99)
	    return "" + n;
	if (n > 9)
	    return "0" + n;
	return "00" + n;
    }
    
    private void flushout()
      throws IOException {
	fsock_out.print("\r");
	fsock_out.flush();
    }

    public GameClient(int side, String host, int server)
      throws IOException {
	InetAddress addr = InetAddress.getByName(host);
	sock = new Socket(addr, server_base + server);
	InputStream instream = sock.getInputStream();
	fsock_in = new BufferedReader(new InputStreamReader(instream));
	OutputStream outstream = sock.getOutputStream();
	fsock_out = new PrintStream(new BufferedOutputStream(outstream));

	get_msg();
	if (msg_code != 0)
	    throw new IOException("illegal greeeting " + zeropad(msg_code));
	fsock_out.print(client_version + " player");
	if (side == WHO_WHITE)
	    fsock_out.print(" white");
	else
	    fsock_out.print(" black");
	flushout();
	get_msg();
	if (msg_code != 100 && msg_code != 101)
	    throw new IOException("side failure " + zeropad(msg_code));
	if (msg_code == 101) {
	    time_controls = true;
	    get_time_controls();
	    if (side == WHO_WHITE) {
		my_time = white_time_control;
		opp_time = black_time_control;
	    } else {
		opp_time = black_time_control;
		my_time = white_time_control;
	    }
	}
	get_msg();
	if ((msg_code != 351 && side == WHO_WHITE) ||
	    (msg_code != 352 && side == WHO_BLACK))
	    throw new IOException("side failure " + zeropad(msg_code));
	who = side;
    }

    public int make_move(Move m)
      throws IOException {
	String ellipses = "";
  
	if (who == WHO_NONE)
	    throw new IOException("not initialized");
	if (winner != WHO_NONE)
	    throw new IOException("game over");
	if (who == WHO_WHITE)
	    ellipses = " ...";
	fsock_out.print(serial + ellipses + " " + m.name());
	flushout();
	get_msg();
	switch(msg_code) {
	case 201:
	    winner = who;
	    break;
	case 202:
	    winner = opponent(who);
	    break;
	case 203:
	    winner = WHO_OTHER;
	    break;
	}
	if (winner != WHO_NONE) {
	    close();
	    return STATE_DONE;
	}
	if (msg_code != 200 && msg_code != 207)
	    throw new IOException("bad result code " + zeropad(msg_code));
	if (msg_code == 207)
	  my_time = get_time();
	get_msg();
	if (msg_code < 311 || msg_code > 314)
	    throw new IOException("bad status code " + zeropad(msg_code));
	serial++;
	return STATE_CONTINUE;
    }

    public int get_move()
      throws IOException {
	if (who == WHO_NONE)
	    throw new IOException("not initialized");
	if (winner != WHO_NONE)
	    throw new IOException("game over");
	get_msg();
	if ((msg_code < 311 || msg_code > 326) && msg_code != 361 && msg_code != 362)
	    throw new IOException("bad status code " + zeropad(msg_code));
	if ((who == WHO_WHITE &&
	     (msg_code == 312 || msg_code == 314 || msg_code == 323 ||
	      msg_code == 324 || msg_code == 326)) ||
	    (who == WHO_BLACK &&
	     (msg_code == 311 || msg_code == 313 || msg_code == 321 ||
	      msg_code == 322 || msg_code == 325)))
	    throw new IOException("status code " +
				  zeropad(msg_code) +
				  " from wrong side");
	switch(who) {
	case WHO_WHITE:
	    switch(msg_code) {
	    case 311:
	    case 313:
		move = parse_move();
		return STATE_CONTINUE;
	    case 321:
		move = parse_move();
		winner = WHO_BLACK;
		return STATE_DONE;
	    case 361:
		winner = WHO_BLACK;
		return STATE_DONE;
	    case 322:
		move = parse_move();
		winner = WHO_WHITE;
		return STATE_DONE;
	    case 362:
		winner = WHO_WHITE;
		return STATE_DONE;
	    case 325:
		move = parse_move();
		winner = WHO_OTHER;
		return STATE_DONE;
	    }
	    break;
	case WHO_BLACK:
	    switch(msg_code) {
	    case 312:
	    case 314:
		move = parse_move();
		return STATE_CONTINUE;
	    case 323:
		move = parse_move();
		winner = WHO_WHITE;
		return STATE_DONE;
	    case 362:
		winner = WHO_WHITE;
		return STATE_DONE;
	    case 324:
		move = parse_move();
		winner = WHO_BLACK;
		return STATE_DONE;
	    case 361:
		winner = WHO_BLACK;
		return STATE_DONE;
	    case 326:
		move = parse_move();
		winner = WHO_OTHER;
		return STATE_DONE;
	    }
	    break;
	}
	throw new IOException("unknown status code " + zeropad(msg_code));
    }

    // attempt to get the socket etc. closed
    // if necessary before the object is abandoned
    protected void finalize()
	throws Throwable {
	close();
    }
}
