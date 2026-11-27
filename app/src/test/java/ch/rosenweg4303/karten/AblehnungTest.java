package ch.rosenweg4303.karten;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AblehnungTest {
	@Test
	public void textMitGrund() {
		assertEquals("Anlage lehnt die Anmeldung ab (401): Geraet nicht berechtigt",
				Ablehnung.text(401, "Geraet nicht berechtigt"));
	}

	@Test
	public void textOhneGrund() {
		assertEquals("Anlage lehnt die Anmeldung ab (401): ohne Begründung", Ablehnung.text(401, null));
		assertEquals("Anlage lehnt die Anmeldung ab (401): ohne Begründung", Ablehnung.text(401, "  \n "));
	}

	@Test
	public void textEinzeiligUndGekuerzt() {
		StringBuilder lang = new StringBuilder("a\nb");
		for (int i = 0; i < 300; i++) lang.append('x');
		String t = Ablehnung.text(401, lang.toString());
		assertTrue(t.startsWith("Anlage lehnt die Anmeldung ab (401): a b"));
		assertFalse(t.contains("\n"));
		assertTrue(t.endsWith("…"));
		assertEquals("Anlage lehnt die Anmeldung ab (401): ".length() + 200, t.length());
	}

	@Test
	public void gleichNachAnmeldung() {
		assertFalse("ohne Anmeldung", Ablehnung.gleichNachAnmeldung(0, 5_000));
		assertTrue(Ablehnung.gleichNachAnmeldung(1_000, 2_000));
		assertTrue(Ablehnung.gleichNachAnmeldung(1_000, 1_000 + Ablehnung.FRIST_MS - 1));
		assertFalse("Frist vorbei", Ablehnung.gleichNachAnmeldung(1_000, 1_000 + Ablehnung.FRIST_MS));
		assertFalse("Uhr rueckwaerts", Ablehnung.gleichNachAnmeldung(5_000, 1_000));
	}
}
