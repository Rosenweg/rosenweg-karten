package ch.rosenweg4303.karten;

/**
 * Texte und Regeln, wenn die Anlage eine Anmeldung ablehnt (ohne Rosenweg Geraet).
 *
 * Anlass 27.11.2026, Rosenweg Karten 1.6.2: die Anlage antwortete auf jede App-Anfrage mit
 * 401, die App meldete still ab und zeigte den Anmeldeschirm - Karte hinhalten, 401, Anmeldeschirm
 * (Schleife). Seit 1.6.3 zeigt sie stattdessen den Grund und laesst die Person entscheiden.
 * Ohne Android-Abhaengigkeiten, damit es ein JVM-Test pruefen kann.
 */
final class Ablehnung {
	/** So lange nach der Rueckkehr vom Anmeldeschirm gilt "abgemeldet" als Fehler, nicht als Ablauf. */
	static final long FRIST_MS = 2 * 60 * 1000L;

	static final String VERLOREN = "Die Anmeldung ging gleich danach wieder verloren "
			+ "(Sitzung am Server beendet oder Geräteschlüssel fehlt).";

	private static final int MAX = 200;

	private Ablehnung() {
	}

	/** "Anlage lehnt die Anmeldung ab (401): Grund" - Grund vom Server, gekuerzt, ohne Zeilenumbrueche. */
	static String text(int status, String grund) {
		String g = grund == null ? "" : grund.replaceAll("\\s+", " ").trim();
		if (g.length() > MAX) g = g.substring(0, MAX - 1) + "…";
		if (g.isEmpty()) g = "ohne Begründung";
		return "Anlage lehnt die Anmeldung ab (" + status + "): " + g;
	}

	/** true: "abgemeldet" kam kurz nach einer Anmeldung - nicht wieder den Anmeldeschirm zeigen. */
	static boolean gleichNachAnmeldung(long angemeldetUm, long jetzt) {
		return angemeldetUm > 0 && jetzt >= angemeldetUm && jetzt - angemeldetUm < FRIST_MS;
	}
}
