package com.example.paripariapp.ui.view;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.paripariapp.R;
import com.example.paripariapp.databinding.ActivityDettaglioSchedaBinding;

/**
 * Activity dedicata alla visualizzazione e gestione del dettaglio di una scheda.
 * Isola il ciclo di vita e la memoria della scheda da MainActivity.
 */
public class DettaglioSchedaActivity extends AppCompatActivity {

    public static final String EXTRA_SCHEDA_ID = "extra_scheda_id";
    public static final String EXTRA_TITOLO = "extra_titolo";
    public static final String EXTRA_VALUTA = "extra_valuta";

    private ActivityDettaglioSchedaBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityDettaglioSchedaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.dettaglioContainer, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        if (savedInstanceState == null) {
            String schedaId = getIntent().getStringExtra(EXTRA_SCHEDA_ID);
            String titolo = getIntent().getStringExtra(EXTRA_TITOLO);
            String valuta = getIntent().getStringExtra(EXTRA_VALUTA);

            DettaglioSchedaFragment fragment = DettaglioSchedaFragment.newInstance(schedaId, titolo, valuta);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.dettaglio_container, fragment)
                    .commit();
        }
    }
}
