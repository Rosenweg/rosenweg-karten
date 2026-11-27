package ch.rosenweg4303.karten;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import ch.rosenweg4303.cloud.KartenSitzung;
import ch.rosenweg4303.cloud.RosenwegAnmeldung;

/**
 * Einstellungen wie in den anderen Rosenweg-Apps: wer angemeldet ist und womit (Rosenweg-Karte,
 * QR-Code oder Rosenweg-Konto), "Abmelden" (nur diese App) und die Version.
 *
 * Geteiltes Telefon (Rosenweg Geraet da): die App hat keine eigene Anmeldung, angemeldet und
 * abgemeldet wird am Sperrbildschirm - darum dort kein "Abmelden".
 */
public class EinstellungenActivity extends Activity {
	private TextView anmeldung;
	private Button abmelden;

	@Override
	protected void onCreate(Bundle b) {
		super.onCreate(b);
		setContentView(baue());
	}

	@Override
	protected void onResume() {
		super.onResume();
		zeigeStand();
	}

	/** "Angemeldet als Stefan Müller (Rosenweg-Karte)" usw. */
	static String anmeldeText(Activity a) {
		if (RosenwegAnmeldung.INSTANCE.geraetDa(a)) {
			return "Geteiltes Telefon: angemeldet wird am Sperrbildschirm (Rosenweg Gerät).";
		}
		if (!RosenwegAnmeldung.INSTANCE.angemeldet(a)) return "Nicht angemeldet";
		String wer = RosenwegAnmeldung.INSTANCE.name(a);
		if (wer == null || wer.trim().isEmpty()) wer = RosenwegAnmeldung.INSTANCE.email(a);
		String wie;
		if (!RosenwegAnmeldung.INSTANCE.mitKarte(a)) wie = "Rosenweg-Konto";
		else if ("mit QR-Code".equals(KartenSitzung.INSTANCE.weg(a))) wie = "QR-Code";
		else wie = "Rosenweg-Karte";
		return "Angemeldet" + (wer == null || wer.isEmpty() ? "" : " als " + wer) + " (" + wie + ")";
	}

	private void zeigeStand() {
		anmeldung.setText(anmeldeText(this));
		boolean eigenesHandy = !RosenwegAnmeldung.INSTANCE.geraetDa(this);
		abmelden.setVisibility(eigenesHandy && RosenwegAnmeldung.INSTANCE.angemeldet(this) ? View.VISIBLE : View.GONE);
	}

	private void abmeldenFragen() {
		new AlertDialog.Builder(this)
				.setTitle("Abmelden?")
				.setMessage("Aus Rosenweg Karten abmelden? Das gilt nur für diese App. "
						+ "Danach meldest du dich wieder mit Karte, Rosenweg-Konto oder QR-Code an.")
				.setPositiveButton("Abmelden", (d, w) -> {
					RosenwegAnmeldung.INSTANCE.abmelden(this);
					// KartenActivity zeigt beim Zurueckkommen den Anmeldeschirm
					finish();
				})
				.setNegativeButton("Abbrechen", null)
				.show();
	}

	// ─── Oberflaeche (wie KartenActivity, ohne Layout-Dateien) ──────────────

	private int dp(float v) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
	}

	private TextView text(float sp, int farbe, boolean fett) {
		TextView t = new TextView(this);
		t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
		t.setTextColor(farbe);
		t.setGravity(Gravity.START);
		if (fett) t.setTypeface(Typeface.DEFAULT_BOLD);
		return t;
	}

	private Button knopf(String beschriftung, String farbe) {
		Button k = new Button(this);
		k.setText(beschriftung);
		k.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
		k.setTextColor(Color.WHITE);
		k.setAllCaps(false);
		GradientDrawable g = new GradientDrawable();
		g.setColor(Color.parseColor(farbe));
		g.setCornerRadius(dp(14));
		k.setBackground(g);
		return k;
	}

	private String version() {
		try {
			PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
			long code = android.os.Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : alterCode(p);
			return "Version " + p.versionName + " (" + code + ")";
		} catch (Exception e) {
			return "";
		}
	}

	@SuppressWarnings("deprecation")
	private static long alterCode(PackageInfo p) {
		return p.versionCode;
	}

	private View baue() {
		int hell = Color.parseColor("#E5E7EB"), grau = Color.parseColor("#94A3B8");
		LinearLayout l = new LinearLayout(this);
		l.setOrientation(LinearLayout.VERTICAL);
		l.setBackgroundColor(Color.parseColor("#0F172A"));
		l.setOnApplyWindowInsetsListener((v, ins) -> {
			v.setPadding(dp(24) + ins.getSystemWindowInsetLeft(), dp(24) + ins.getSystemWindowInsetTop(),
					dp(24) + ins.getSystemWindowInsetRight(), dp(16) + ins.getSystemWindowInsetBottom());
			return ins;
		});

		TextView titel = text(26, hell, true);
		titel.setText("Einstellungen");
		titel.setPadding(0, 0, 0, dp(20));
		l.addView(titel);

		TextView kopf = text(13, grau, true);
		kopf.setText("ANMELDUNG");
		l.addView(kopf);

		anmeldung = text(17, hell, false);
		anmeldung.setPadding(0, dp(6), 0, dp(4));
		l.addView(anmeldung);

		TextView nurApp = text(14, grau, false);
		nurApp.setText(RosenwegAnmeldung.INSTANCE.geraetDa(this)
				? "Wer Karten verwalten darf (Technik, Präsident), prüft die Telefonanlage für die angemeldete Person."
				: "Die Anmeldung gilt nur für Rosenweg Karten. Karten verwalten dürfen Technik und Präsident.");
		nurApp.setPadding(0, 0, 0, dp(16));
		l.addView(nurApp);

		abmelden = knopf("Abmelden", "#DC2626");
		abmelden.setOnClickListener(v -> abmeldenFragen());
		l.addView(abmelden, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

		View platz = new View(this);
		l.addView(platz, new LinearLayout.LayoutParams(1, 0, 1f));

		TextView ver = text(13, grau, false);
		ver.setText("Rosenweg Karten · " + version());
		ver.setGravity(Gravity.CENTER);
		ver.setPadding(0, 0, 0, dp(12));
		l.addView(ver);

		Button zurueck = knopf("Zurück", "#334155");
		zurueck.setOnClickListener(v -> finish());
		l.addView(zurueck, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
		return l;
	}
}
