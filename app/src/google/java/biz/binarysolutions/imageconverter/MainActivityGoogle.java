package biz.binarysolutions.imageconverter;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingClient.SkuType;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.Purchase.PurchasesResult;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import biz.binarysolutions.imageconverter.data.OutputFormat;
import biz.binarysolutions.imageconverter.exceptions.ConvertException;
import biz.binarysolutions.imageconverter.exceptions.DecodeException;
import biz.binarysolutions.imageconverter.listeners.RestrictedOutputFormatListener;

/**
 *
 */
public class MainActivityGoogle extends MainActivity
    implements
        PurchasesUpdatedListener,
        BillingClientStateListener,
        SkuDetailsResponseListener,
        AcknowledgePurchaseResponseListener,
        DialogInterface.OnClickListener {

    private static final String PREFERENCES_KEY = "purchaseToken";
    private static final String PRODUCT_ID_SUBS =
        "biz.binarysolutions.imageconverter.yearly_subscription";

    private boolean       isFullVersion = false;
    private BillingClient billingClient;
    private SkuDetails    skuDetails;

    private InterstitialAd ad;
    private boolean        showAd = false;

    /**
     *
     */
    private void initializeAd() {

        if (isFullVersion) {
            return;
        }

        MobileAds.initialize(this);

        ad = new InterstitialAd(this);
        ad.setAdUnitId(getString(R.string.admob_ad_id));
        ad.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                showAd = true;
            }
        });
        ad.loadAd(new AdRequest.Builder().build());
    }

    /**
     *
     * @param id
     * @param format
     */
    private void setCheckBoxListener(int id, OutputFormat format) {

        CheckBox checkBox = findViewById(id);
        if (checkBox == null) {
            return;
        }

        checkBox.setOnCheckedChangeListener(
            new RestrictedOutputFormatListener(outputFormats, format, this)
        );
    }

    /**
     *
     */
    private void setCheckBoxListeners() {

        setCheckBoxListener(R.id.checkBoxBMP,  OutputFormat.BMP);
        setCheckBoxListener(R.id.checkBoxTIF,  OutputFormat.TIF);
    }

    /**
     *
     */
    private void initializeBillingClient() {

        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases()
            .build();

        billingClient.startConnection(this);
    }

    /**
     *
     */
    private void queryAvailablePurchases() {

        List<String> skuList = new ArrayList<>();
        skuList.add(PRODUCT_ID_SUBS);
        SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
        params.setSkusList(skuList).setType(SkuType.SUBS);
        billingClient.querySkuDetailsAsync(params.build(), this);
    }

    /**
     *
     */
    private void queryPastPurchases() {

        if (billingClient == null) {
            return;
        }

        PurchasesResult result = billingClient.queryPurchases(SkuType.SUBS);
        onPurchasesUpdated(result.getBillingResult(), result.getPurchasesList());
    }

    /**
     *
     * @param textView
     */
    private void updatePrice(@NonNull TextView textView) {

        if (skuDetails == null) {
            return;
        }

        String oldPrice = Matcher.quoteReplacement(getString(R.string.price));
        String newPrice = Matcher.quoteReplacement(skuDetails.getPrice());
        String oldText  = getString(R.string.purchase_text_price);
        String newText  = oldText.replaceAll(oldPrice, newPrice);

        textView.setText(newText);
    }

    /**
     *
     * @param purchase
     */
    private void acknowledgePurchase(Purchase purchase) {

        if (purchase.isAcknowledged()) {
            return;
        }

        AcknowledgePurchaseParams params =
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        if (billingClient != null) {
            billingClient.acknowledgePurchase(params, this);
        }
    }

    /**
     *
     */
    private void deletePurchaseToken() {
        savePurchaseToken("");
        isFullVersion = false;
    }

    /**
     *
     */
    private void readPurchaseToken() {

        SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(this);

        String purchaseToken = preferences.getString(PREFERENCES_KEY, "");
        isFullVersion = !TextUtils.isEmpty(purchaseToken);
    }

    /**
     *
     * @param purchaseToken
     */
    private void savePurchaseToken(String purchaseToken) {

        SharedPreferences.Editor editor =
            PreferenceManager.getDefaultSharedPreferences(this).edit();

        editor.putString(PREFERENCES_KEY, purchaseToken);
        editor.apply();

        isFullVersion = true;
    }

    /**
     * TODO: verify purchase
     *  https://developer.android.com/google/play/billing/security#verify
     *  https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptions/get
     *
     * @param purchase
     */
    private void handlePurchase(Purchase purchase) {

        int state = purchase.getPurchaseState();
        if (state != Purchase.PurchaseState.PURCHASED) {
            deletePurchaseToken();
        } else {
            savePurchaseToken(purchase.getPurchaseToken());
            acknowledgePurchase(purchase);
        }
    }

    @Override
    protected void convertUsingNonNativeAPI(File file, OutputFormat format)
        throws ConvertException {

        if (isFullVersion) {
            super.convertUsingNonNativeAPI(file, format);
        } else {
            throw new DecodeException();
        }
    }

    @Override
    protected void onErrorDialogDismissed() {
        displayPurchaseDialog(R.string.input_format_not_available);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        readPurchaseToken();

        initializeBillingClient();
        initializeAd();
        setCheckBoxListeners();
    }

    /**
     *
     * @return
     */
    public boolean isFullVersion() {
        return isFullVersion;
    }

    /**
     *
     * @param messageId
     */
    public void displayPurchaseDialog(int messageId) {

        LayoutInflater inflater = LayoutInflater.from(this);
        View container = inflater.inflate(R.layout.dialog_purchase, null);

        TextView textView;

        textView = container.findViewById(R.id.textViewPurchaseIntro);
        if (textView != null) {
            textView.setText(messageId);
        }

        textView = container.findViewById(R.id.textViewPrice);
        if (textView != null) {
            updatePrice(textView);
        }

        new AlertDialog.Builder(this)
            .setTitle(android.R.string.dialog_alert_title)
            .setView(container)
            .setPositiveButton(getString(R.string.unlock_full_version), this)
            .create()
            .show();
    }

    @Override
    public void onPurchasesUpdated
        (
            @NonNull  BillingResult  result,
            @Nullable List<Purchase> purchases
        ) {

        if (result.getResponseCode() != BillingResponseCode.OK) {
            return;
        }
        if (purchases == null || purchases.size() == 0) {
            deletePurchaseToken();
            return;
        }

        for (Purchase purchase : purchases) {
            handlePurchase(purchase);
        }
    }

    @Override
    public void onBillingSetupFinished(@NonNull BillingResult billingResult) {

        if (billingResult.getResponseCode() !=  BillingResponseCode.OK) {
            return;
        }

        queryAvailablePurchases();
        queryPastPurchases();
    }

    @Override
    public void onBillingServiceDisconnected() {
        // TODO:
        //  Try to restart the connection on the next request to
        //  Google Play by calling the startConnection() method.
    }

    @Override
    public void onSkuDetailsResponse
        (
            @NonNull  BillingResult    result,
            @Nullable List<SkuDetails> list
        ) {

        if (result.getResponseCode() != BillingResponseCode.OK) {
            return;
        }
        if (list == null) {
            return;
        }

        for (SkuDetails skuDetails : list) {

            String sku = skuDetails.getSku();
            if (PRODUCT_ID_SUBS.equals(sku)) {
                this.skuDetails = skuDetails;
            }
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {

        if (skuDetails == null) {
            Toast.makeText(this, R.string.try_again, Toast.LENGTH_LONG).show();
            return;
        }

        BillingFlowParams params = BillingFlowParams.newBuilder()
            .setSkuDetails(skuDetails)
            .build();

        billingClient.launchBillingFlow(this, params);
    }

    @Override
    public void onAcknowledgePurchaseResponse(@NonNull BillingResult result) {
        // purchase acknowledged
    }

    @Override
    public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);

        if (showAd) {
            ad.show();
            showAd = false;
        }
    }
}
