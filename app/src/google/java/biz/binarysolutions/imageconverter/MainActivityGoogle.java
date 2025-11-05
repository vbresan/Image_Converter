package biz.binarysolutions.imageconverter;

import static com.android.billingclient.api.BillingClient.SkuType.SUBS;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsParams.Product;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

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
        AcknowledgePurchaseResponseListener,
        ProductDetailsResponseListener,
        DialogInterface.OnClickListener {

    private static final String PREFERENCES_KEY = "purchaseToken";
    private static final String PRODUCT_ID_SUBS =
        "biz.binarysolutions.imageconverter.yearly_subscription";

    private boolean        isFullVersion = false;
    private BillingClient  billingClient;
    private ProductDetails productDetails;

    private InterstitialAd interstitialAd;

    private void initializeAd() {

        if (isFullVersion) {
            return;
        }

        MobileAds.initialize(this, initializationStatus -> InterstitialAd.load(
            this,
            getString(R.string.admob_ad_id),
            new AdRequest.Builder().build(),
            new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd ad) {
                    interstitialAd = ad;
                    interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            interstitialAd = null;
                        }

                        @Override
                        public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                            interstitialAd = null;
                        }
                    });
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError error) {
                    interstitialAd = null;
                }
        }));
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

    private PendingPurchasesParams getPendingPurchaseParams() {
        return PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .enablePrepaidPlans()
            .build();
    }

    private void initializeBillingClient() {

        billingClient = BillingClient.newBuilder(this)
            .enablePendingPurchases(getPendingPurchaseParams())
            .setListener(this)
            .build();

        billingClient.startConnection(this);
    }

    private List<Product> getProductList() {

        Product product = Product.newBuilder()
            .setProductId(PRODUCT_ID_SUBS)
            .setProductType(BillingClient.ProductType.SUBS)
            .build();

        List<Product> list = new ArrayList<>();
        list.add(product);

        return list;
    }

    private void queryAvailablePurchases() {

        if (billingClient == null) {
            return;
        }

        List<Product> productList = getProductList();
        QueryProductDetailsParams params =
            QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(params, this);
    }

    private QueryPurchasesParams getQueryPurchasesParams() {
        return QueryPurchasesParams.newBuilder().setProductType(SUBS).build();
    }

    private void queryPastPurchases() {

        if (billingClient == null) {
            return;
        }

        QueryPurchasesParams params = getQueryPurchasesParams();
        billingClient.queryPurchasesAsync(params, this::onPurchasesUpdated);
    }

    @SuppressWarnings("ConstantConditions")
    private String getPrice(ProductDetails details) {

        String price = "";
        try {
            price = details
                .getSubscriptionOfferDetails()
                .get(0)
                .getPricingPhases()
                .getPricingPhaseList()
                .get(0)
                .getFormattedPrice();
        } catch (Exception e) {
            // do nothing
        }

        return price;
    }

    private void updatePrice(@NonNull TextView textView) {

        if (productDetails == null) {
            return;
        }

        String oldPrice = Matcher.quoteReplacement(getString(R.string.price));
        String newPrice = Matcher.quoteReplacement(getPrice(productDetails));
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

    @SuppressWarnings("ConstantConditions")
    private String justGetTheFuckingOfferToken() {

        String offerToken = "";
        try {
            offerToken = productDetails
                .getSubscriptionOfferDetails()
                .get(0)
                .getOfferToken();
        } catch (Exception e) {
            // do nothing
        }

        return offerToken;
    }

    private List<ProductDetailsParams> getProductDetailsParamsList() {

        ProductDetailsParams params = ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(justGetTheFuckingOfferToken())
            .build();

        List<ProductDetailsParams> list = new ArrayList<>();
        list.add(params);

        return list;
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
    public void onProductDetailsResponse
        (
            @NonNull BillingResult             billingResult,
            @NonNull QueryProductDetailsResult queryResult
        ) {

        if (billingResult.getResponseCode() != BillingResponseCode.OK) {
            return;
        }

        List<ProductDetails> list = queryResult.getProductDetailsList();
        for (ProductDetails details: list) {

            String productId = details.getProductId();
            if (PRODUCT_ID_SUBS.equals(productId)) {
                productDetails = details;
            }
        }
    }

    @Override
    public void onAcknowledgePurchaseResponse(@NonNull BillingResult result) {
        // purchase acknowledged
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {

        if (productDetails == null || billingClient == null) {
            Toast.makeText(this, R.string.try_again, Toast.LENGTH_LONG).show();
            return;
        }

        List<ProductDetailsParams> list = getProductDetailsParamsList();
        BillingFlowParams params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(list)
            .build();

        billingClient.launchBillingFlow(this, params);
    }

    @Override
    public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);

        if (interstitialAd != null) {
            interstitialAd.show(this);
        }
    }
}
