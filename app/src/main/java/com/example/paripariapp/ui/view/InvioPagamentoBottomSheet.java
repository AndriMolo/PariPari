package com.example.paripariapp.ui.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.MainActivity;
import com.example.paripariapp.R;
import com.example.paripariapp.data.model.TrasferimentoSaldo;
import com.example.paripariapp.data.repository.UserPreferencesRepository;
import com.example.paripariapp.databinding.BottomSheetInvioPagamentoBinding;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;
import com.example.paripariapp.util.AppSnackbar;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

/**
 * BottomSheet per l'avvio dei pagamenti, saldi tra utenti e condivisione dei link PayPal/Revolut.
 */
public class InvioPagamentoBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_DA_ID = "arg_da_id";
    private static final String ARG_DA_NOME = "arg_da_nome";
    private static final String ARG_A_ID = "arg_a_id";
    private static final String ARG_A_NOME = "arg_a_nome";
    private static final String ARG_IMPORTO = "arg_importo";
    private static final String ARG_VALUTA = "arg_valuta";
    private static final String ARG_SCHEDA_ID = "arg_scheda_id";
    private static final String ARG_PAYPAL_HANDLE = "arg_paypal_handle";
    private static final String ARG_REVOLUT_HANDLE = "arg_revolut_handle";
    private static final String ARG_MY_PARTICIPANT_ID = "arg_my_participant_id";

    private BottomSheetInvioPagamentoBinding binding;

    private String daId;
    private String daNome;
    private String aId;
    private String aNome;
    private double importo;
    private String valuta;
    private String schedaId;
    private String creditorePaypalHandle;
    private String creditoreRevolutHandle;
    private String myParticipantId;

    public static InvioPagamentoBottomSheet newInstance(TrasferimentoSaldo t, String schedaId, String paypalHandle, String revolutHandle, String myParticipantId) {
        InvioPagamentoBottomSheet sheet = new InvioPagamentoBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_DA_ID, t.getDaPartecipanteId());
        args.putString(ARG_DA_NOME, t.getDaPartecipanteNome());
        args.putString(ARG_A_ID, t.getAPartecipanteId());
        args.putString(ARG_A_NOME, t.getAPartecipanteNome());
        args.putDouble(ARG_IMPORTO, t.getImporto());
        args.putString(ARG_VALUTA, t.getValuta());
        args.putString(ARG_SCHEDA_ID, schedaId);
        args.putString(ARG_PAYPAL_HANDLE, paypalHandle);
        args.putString(ARG_REVOLUT_HANDLE, revolutHandle);
        args.putString(ARG_MY_PARTICIPANT_ID, myParticipantId);
        sheet.setArguments(args);
        return sheet;
    }

    public static InvioPagamentoBottomSheet newInstance(TrasferimentoSaldo t, String schedaId, String paypalHandle, String revolutHandle) {
        return newInstance(t, schedaId, paypalHandle, revolutHandle, null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            daId = getArguments().getString(ARG_DA_ID);
            daNome = getArguments().getString(ARG_DA_NOME);
            aId = getArguments().getString(ARG_A_ID);
            aNome = getArguments().getString(ARG_A_NOME);
            importo = getArguments().getDouble(ARG_IMPORTO);
            valuta = getArguments().getString(ARG_VALUTA, "EUR");
            schedaId = getArguments().getString(ARG_SCHEDA_ID);
            creditorePaypalHandle = getArguments().getString(ARG_PAYPAL_HANDLE);
            creditoreRevolutHandle = getArguments().getString(ARG_REVOLUT_HANDLE);
            myParticipantId = getArguments().getString(ARG_MY_PARTICIPANT_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetInvioPagamentoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UserPreferencesRepository prefs = UserPreferencesRepository.getInstance(requireContext());

        String aNomePulito = com.example.paripariapp.data.model.Partecipante.pulisciNome(aNome);
        String daNomePulito = com.example.paripariapp.data.model.Partecipante.pulisciNome(daNome);

        boolean isDebitore = (daId != null && daId.equals(myParticipantId))
                || (daNomePulito.equalsIgnoreCase("io") || daNomePulito.equalsIgnoreCase("me"));

        boolean isCreditore = (aId != null && aId.equals(myParticipantId))
                || (aNomePulito.equalsIgnoreCase("io") || aNomePulito.equalsIgnoreCase("me"));

        String strImportoValuta = com.example.paripariapp.util.ImportoUtil.formatta(importo, valuta);

        String paypalHandleToUse;
        String revolutHandleToUse;

        if (isDebitore) {
            // Se devi dare soldi tu -> Schermata "Salda il Debito"
            binding.tvTitoloPagamento.setText(R.string.titolo_salda_debito);
            binding.tvDescrizioneSaldo.setText(getString(R.string.desc_salda_debito, strImportoValuta, aNomePulito));
        } else if (isCreditore) {
            // Se devono dare soldi a te -> Schermata "Richiedi Pagamento"
            binding.tvTitoloPagamento.setText(R.string.titolo_richiedi_pagamento);
            binding.tvDescrizioneSaldo.setText(getString(R.string.desc_richiedi_pagamento, daNomePulito, strImportoValuta));
        } else {
            // Saldo generico tra altri partecipanti
            binding.tvTitoloPagamento.setText(R.string.titolo_bottom_sheet_pagamento);
            binding.tvDescrizioneSaldo.setText(getString(R.string.saldi_descrizione_trasferimento, daNomePulito, aNomePulito) + " (" + strImportoValuta + ")");
        }

        if (!TextUtils.isEmpty(creditorePaypalHandle)) {
            paypalHandleToUse = creditorePaypalHandle;
        } else if (isCreditore) {
            paypalHandleToUse = prefs.getPaypalHandle();
        } else {
            paypalHandleToUse = "";
        }

        if (!TextUtils.isEmpty(creditoreRevolutHandle)) {
            revolutHandleToUse = creditoreRevolutHandle;
        } else if (isCreditore) {
            revolutHandleToUse = prefs.getRevolutHandle();
        } else {
            revolutHandleToUse = "";
        }

        final String finalPaypalLink = UserPreferencesRepository.generatePaypalLink(paypalHandleToUse, importo, valuta);
        final String finalRevolutLink = UserPreferencesRepository.generateRevolutLink(revolutHandleToUse, importo, valuta);

        boolean haPaypal = !TextUtils.isEmpty(finalPaypalLink);
        boolean haRevolut = !TextUtils.isEmpty(finalRevolutLink);

        // Pulsante Segna come saldato
        binding.btnSegnaSaldato.setOnClickListener(v -> {
            if (schedaId != null && daId != null && aId != null) {
                SpeseViewModel viewModel = new ViewModelProvider(requireActivity()).get(SpeseViewModel.class);
                viewModel.registraPagamento(schedaId, daId, daNome, aId, aNome, importo, valuta);
                Toast.makeText(requireContext(), R.string.msg_operazione_completata, Toast.LENGTH_SHORT).show();
                dismiss();
            } else {
                dismiss();
            }
        });

        // PayPal Card
        if (haPaypal) {
            binding.cardPaypal.setVisibility(View.VISIBLE);
            binding.tvPaypalBadge.setText(getString(R.string.format_handle, UserPreferencesRepository.cleanPaypalHandle(paypalHandleToUse)));
            binding.tvPaypalLink.setText(finalPaypalLink);

            binding.tvPaypalLink.setOnClickListener(v -> apriLinkEsterno(finalPaypalLink));
            binding.btnCopiaPaypal.setOnClickListener(v -> copiaInAppunti(finalPaypalLink));
            binding.btnCondividiPaypal.setOnClickListener(v -> condivideMessaggio(
                    getString(R.string.msg_condividi_mio_paypal, daNome, strImportoValuta, finalPaypalLink)
            ));
        } else {
            binding.cardPaypal.setVisibility(View.GONE);
        }

        // Revolut Card
        if (haRevolut) {
            binding.cardRevolut.setVisibility(View.VISIBLE);
            binding.tvRevolutBadge.setText(getString(R.string.format_handle, UserPreferencesRepository.cleanRevolutHandle(revolutHandleToUse)));
            binding.tvRevolutLink.setText(finalRevolutLink);

            binding.tvRevolutLink.setOnClickListener(v -> apriLinkEsterno(finalRevolutLink));
            binding.btnCopiaRevolut.setOnClickListener(v -> copiaInAppunti(finalRevolutLink));
            binding.btnCondividiRevolut.setOnClickListener(v -> condivideMessaggio(
                    getString(R.string.msg_condividi_mio_revolut, daNome, strImportoValuta, finalRevolutLink)
            ));
        } else {
            binding.cardRevolut.setVisibility(View.GONE);
        }

        // Se nessun metodo è configurato
        if (!haPaypal && !haRevolut) {
            binding.tvAvvisoNonConfigurato.setVisibility(View.VISIBLE);
            if (isDebitore) {
                binding.tvAvvisoNonConfigurato.setText(getString(R.string.avviso_creditore_non_configurato, aNome));
                binding.btnVaiProfilo.setVisibility(View.GONE);
            } else {
                binding.tvAvvisoNonConfigurato.setText(R.string.avviso_pagamento_non_configurato);
                binding.btnVaiProfilo.setVisibility(View.VISIBLE);
            }
        } else {
            binding.tvAvvisoNonConfigurato.setVisibility(View.GONE);
            binding.btnVaiProfilo.setVisibility(View.GONE);
        }

        binding.btnCondividiGenerico.setOnClickListener(v -> condivideMessaggio(
                getString(R.string.msg_condividi_generico, daNome, strImportoValuta, aNome)
        ));

        binding.btnVaiProfilo.setOnClickListener(v -> {
            dismiss();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigaVersoAccount();
            }
        });
    }

    private void apriLinkEsterno(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            if (binding != null) {
                AppSnackbar.show(binding.getRoot(), R.string.msg_errore_apertura_link);
            }
        }
    }

    private void copiaInAppunti(String testo) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Link pagamento", testo);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            if (binding != null) {
                AppSnackbar.show(binding.getRoot(), R.string.msg_link_copiato);
            }
        }
    }

    private void condivideMessaggio(String messaggio) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, messaggio);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.title_condividi)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
