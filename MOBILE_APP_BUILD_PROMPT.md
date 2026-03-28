# AgriShield Mobile App - Complete Build Specification

## Executive Summary

Build a production-ready mobile application for **AgriShield** - an agricultural input verification platform. This app enables farmers to:

1. **Authenticate** - SMS-based OTP login for offline-first access
2. **Scan & Verify** - QR code scanning for input authenticity verification
3. **Store Results** - Offline-first SQLite database with sync-on-connect
4. **Report Issues** - Document counterfeit products with photos/location
5. **View History** - Track verification history across devices

**Target Platforms:** Android 12+ (primary), iOS 14+ (secondary)  
**Primary Language:** Kotlin (Android), Swift (iOS)  
**UI Framework:** Jetpack Compose (Android), SwiftUI (iOS)  
**Backend API:** https://Agrishield.com/api (with fallback to cached data)

---

## Part 1: Android App Specification

### 1.1 Project Setup

```kotlin
// build.gradle.kts (Project Level)
plugins {
    id("com.android.application") version "8.1.0"
    kotlin("android") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
}

android {
    compileSdk = 34
    targetSdk = 34
    minSdk = 28  // Android 9+
    namespace = "com.agrishield.farmer"

    defaultConfig {
        applicationId = "com.agrishield.farmer"
        versionCode = 1
        versionName = "1.0.0"
        
        // Build features
        buildFeatures {
            compose = true
            aidl = false
        }

        composeOptions {
            kotlinCompilerExtensionVersion = "1.5.0"
        }
    }
}

// app/build.gradle.kts
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    // Jetpack Compose (UI)
    implementation(platform("androidx.compose:compose-bom:2023.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.0")
    
    // Data Storage
    implementation("androidx.room:room-runtime:2.6.0")
    kapt("androidx.room:room-compiler:2.6.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.retrofit2:retrofit:2.10.0")
    implementation("com.squareup.retrofit2:converter-gson:2.10.0")
    
    // QR Code Scanning
    implementation("com.google.mlkit:barcode-scanning:17.1.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")
    
    // Authentication & SMS
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("androidx.biometric:biometric:1.1.0")
    
    // Location (for report context)
    implementation("com.google.android.gms:play-services-location:21.0.1")
    
    // Coroutines & Async
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
    
    // DI
    implementation("io.insert-koin:koin-android:3.5.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

### 1.2 Authentication Flow (SMS OTP + Offline)

```kotlin
// data/auth/AuthService.kt
interface AuthService {
    suspend fun requestOtp(phoneNumber: String): Result<OtpResponse>
    suspend fun verifyOtp(phoneNumber: String, otp: String): Result<AuthToken>
    suspend fun refreshToken(): Result<AuthToken>
    suspend fun logout(): Result<Unit>
}

data class OtpResponse(
    val ok: Boolean,
    val message: String,
    val expiresIn: Int  // seconds
)

data class AuthToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val user: UserProfile
)

data class UserProfile(
    val id: String,
    val name: String,
    val phone: String,
    val role: String,  // "farmer"
    val region: String
)

// implementation/AuthServiceImpl.kt
class AuthServiceImpl(
    private val api: AgriShieldApi,
    private val tokenStore: TokenStore,
    private val isOnline: suspend () -> Boolean
) : AuthService {

    override suspend fun requestOtp(phoneNumber: String): Result<OtpResponse> = runCatching {
        if (!isOnline()) throw OfflineException("Cannot request OTP offline")
        
        api.requestOtp(RequestOtpBody(phoneNumber))
    }

    override suspend fun verifyOtp(phoneNumber: String, otp: String): Result<AuthToken> = runCatching {
        if (!isOnline()) {
            // Demo mode for offline verification (local validation)
            if (otp.length != 6) throw InvalidOtpException()
            val localToken = generateLocalToken(phoneNumber)
            tokenStore.save(localToken)
            return Result.success(localToken)
        }
        
        val token = api.verifyOtp(VerifyOtpBody(phoneNumber, otp))
        tokenStore.save(token)
        token
    }

    private fun generateLocalToken(phoneNumber: String): AuthToken {
        // For offline demo - valid for 7 days or until sync
        return AuthToken(
            accessToken = "local_${System.currentTimeMillis()}",
            refreshToken = "",
            expiresAt = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000,
            user = UserProfile(
                id = "demo_${phoneNumber.hashCode()}",
                name = "Farmer (Offline)",
                phone = phoneNumber,
                role = "farmer",
                region = "offline"
            )
        )
    }
}

// data/auth/TokenStore.kt (Secure Storage)
interface TokenStore {
    suspend fun save(token: AuthToken)
    suspend fun get(): AuthToken?
    suspend fun clear()
    suspend fun isExpired(): Boolean
}

// Implementation using EncryptedSharedPreferences
class TokenStoreImpl(context: Context) : TokenStore {
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "agrishield_tokens",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun save(token: AuthToken) = withContext(Dispatchers.IO) {
        val json = Gson().toJson(token)
        encryptedPrefs.edit().putString("auth_token", json).apply()
    }

    override suspend fun get(): AuthToken? = withContext(Dispatchers.IO) {
        val json = encryptedPrefs.getString("auth_token", null) ?: return@withContext null
        Gson().fromJson(json, AuthToken::class.java)
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().clear().apply()
    }

    override suspend fun isExpired(): Boolean {
        val token = get() ?: return true
        return System.currentTimeMillis() > token.expiresAt
    }
}
```

### 1.3 QR Code Scanning (ML Kit)

```kotlin
// ui/scanner/QRScannerScreen.kt
@Composable
fun QRScannerScreen(
    onScanSuccess: (scanResult: QRScanResult) -> Unit,
    onScanError: (error: String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(LocalContext.current) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    var hasCameraPermission by remember { 
        mutableStateOf(false) 
    }

    // Request camera permission
    LaunchedEffect(Unit) {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(LocalContext.current, permission) 
            == PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        }
    }

    if (!hasCameraPermission) {
        PermissionRequest(permission = Manifest.permission.CAMERA)
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(surfaceProvider)
                        }

                        val imageAnalyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(
                                    analysisExecutor,
                                    QRCodeAnalyzer(
                                        onScanSuccess = onScanSuccess,
                                        onScanError = onScanError
                                    )
                                )
                            }

                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalyzer
                        )

                        // Optional: Flash control
                        camera.cameraControl.enableTorch(false)
                    }, ContextCompat.getMainExecutor(context))
                }
            }
        )

        // Overlay UI
        QRScannerOverlay(modifier = Modifier.align(Alignment.Center))
    }
}

// Scanner ImageAnalyzer using ML Kit
class QRCodeAnalyzer(
    private val onScanSuccess: (QRScanResult) -> Unit,
    private val onScanError: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue ?: continue
                    
                    // Parse QR code (expected format: https://verify.agrishield.com?code=xxx)
                    val verificationCode = extractCode(rawValue)
                    if (verificationCode != null) {
                        onScanSuccess(QRScanResult(rawValue, verificationCode))
                    }
                }
            }
            .addOnFailureListener { e ->
                onScanError(e.message ?: "Unknown error")
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun extractCode(rawValue: String): String? {
        return try {
            val uri = Uri.parse(rawValue)
            uri.getQueryParameter("code")?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}

data class QRScanResult(
    val rawUrl: String,
    val verificationCode: String
)
```

### 1.4 Verification Service (Offline-First)

```kotlin
// domain/verification/VerificationService.kt
interface VerificationService {
    suspend fun verifyInput(code: String): Result<VerificationResult>
    suspend fun saveVerificationLocalCache(result: VerificationResult)
    suspend fun getCachedResult(code: String): VerificationResult?
    suspend fun submitReportIfOnline(report: CounterfeitReport): Result<Unit>
}

data class VerificationResult(
    val code: String,
    val productName: String,
    val manufacturer: String,
    val batchNumber: String,
    val expiryDate: String,
    val isAuthentic: Boolean,
    val confidence: Float,  // 0.0 to 1.0
    val checkedAt: Long,
    val region: String
)

data class CounterfeitReport(
    val verificationCode: String,
    val reportedAt: Long,
    val location: Location,
    val phoneNumber: String,
    val additionalNotes: String,
    val photoUris: List<String>  // Local file paths
)

// implementation/VerificationServiceImpl.kt
class VerificationServiceImpl(
    private val api: AgriShieldApi,
    private val database: AgriShieldDatabase,
    private val isOnline: suspend () -> Boolean
) : VerificationService {

    override suspend fun verifyInput(code: String): Result<VerificationResult> = runCatching {
        // First check local cache
        val cached = getCachedResult(code)
        if (cached != null) {
            return Result.success(cached)
        }

        if (!isOnline()) {
            throw OfflineException("Cannot verify - no internet. Try scanning again when online.")
        }

        // Fetch from backend
        val result = api.verifyInput(VerifyInputRequest(code))
        
        // Cache for offline access
        saveVerificationLocalCache(result)
        
        result
    }

    override suspend fun saveVerificationLocalCache(result: VerificationResult) {
        withContext(Dispatchers.IO) {
            database.verificationDao().insert(
                VerificationEntity(
                    code = result.code,
                    productName = result.productName,
                    manufacturer = result.manufacturer,
                    batchNumber = result.batchNumber,
                    expiryDate = result.expiryDate,
                    isAuthentic = result.isAuthentic,
                    confidence = result.confidence,
                    checkedAt = result.checkedAt,
                    region = result.region,
                    cachedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun getCachedResult(code: String): VerificationResult? {
        return withContext(Dispatchers.IO) {
            database.verificationDao().getByCode(code)?.toDomain()
        }
    }

    override suspend fun submitReportIfOnline(report: CounterfeitReport): Result<Unit> = runCatching {
        if (!isOnline()) {
            // Save to local queue
            database.reportQueueDao().insert(report.toEntity())
            return Result.success(Unit)
        }

        // Submit to backend
        api.submitCounterfeitReport(report)
        
        // Mark as synced
        database.reportQueueDao().deleteByVerificationCode(report.verificationCode)
    }
}

// data/local/Database.kt
@Database(
    entities = [
        VerificationEntity::class,
        ReportQueueEntity::class,
        SyncLogEntity::class
    ],
    version = 1
)
abstract class AgriShieldDatabase : RoomDatabase() {
    abstract fun verificationDao(): VerificationDao
    abstract fun reportQueueDao(): ReportQueueDao
    abstract fun syncLogDao(): SyncLogDao
}
```

### 1.5 Offline Sync Engine

```kotlin
// data/sync/SyncManager.kt
class SyncManager(
    private val api: AgriShieldApi,
    private val database: AgriShieldDatabase,
    private val networkMonitor: NetworkMonitor
) {
    
    fun observeSync(): Flow<SyncState> = flow {
        networkMonitor.isOnline.collect { online ->
            if (online) {
                emit(SyncState.Syncing)
                try {
                    syncReportQueue()
                    syncVerificationCache()
                    emit(SyncState.Success)
                } catch (e: Exception) {
                    emit(SyncState.Error(e.message ?: "Unknown error"))
                }
            } else {
                emit(SyncState.Offline)
            }
        }
    }

    private suspend fun syncReportQueue() {
        val queue = database.reportQueueDao().getAllPending()
        for (report in queue) {
            try {
                api.submitCounterfeitReport(report.toDomain())
                database.reportQueueDao().delete(report)
            } catch (e: Exception) {
                // Retry next sync
                Log.e("SyncManager", "Failed to sync report: ${e.message}")
            }
        }
    }

    private suspend fun syncVerificationCache() {
        // Optional: Refresh cache for frequently verified items
        val recent = database.verificationDao().getRecentCache(limit = 50)
        Log.d("SyncManager", "Synced ${recent.size} cached verifications")
    }
}

sealed class SyncState {
    data object Offline : SyncState()
    data object Syncing : SyncState()
    data object Success : SyncState()
    data class Error(val message: String) : SyncState()
}
```

### 1.6 API Client

```kotlin
// network/AgriShieldApi.kt
interface AgriShieldApi {
    @POST("/api/auth/password/forgot")
    suspend fun requestOtp(
        @Body request: RequestOtpBody
    ): OtpResponse

    @POST("/api/auth/password/reset")
    suspend fun verifyOtp(
        @Body request: VerifyOtpBody
    ): AuthToken

    @POST("/api/verify")
    suspend fun verifyInput(
        @Body request: VerifyInputRequest
    ): VerificationResult

    @POST("/api/verify/report")
    suspend fun submitCounterfeitReport(
        @Body report: CounterfeitReport
    ): Unit

    @GET("/api/verify/history")
    suspend fun getVerificationHistory(
        @Query("limit") limit: Int = 50
    ): List<VerificationResult>
}

// Network client setup
@Module
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val newRequest = original.newBuilder()
                .addHeader("User-Agent", "AgriShield/1.0 (Android)")
                .build()
            chain.proceed(newRequest)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://Agrishield.com")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideAgriShieldApi(retrofit: Retrofit): AgriShieldApi =
        retrofit.create(AgriShieldApi::class.java)
}
```

### 1.7 UI Screens

**Main Navigation Structure:**
```
├─ Authentication (SMS OTP login)
│  ├─ Phone number entry
│  ├─ OTP verification (6 digits)
│  └─ Farmer profile setup
│
├─ Dashboard (Farmer home)
│  ├─ Quick scan button
│  ├─ Recent verifications (3 items)
│  ├─ Sync status indicator
│  └─ Settings/Profile
│
├─ Scanner (QR code capture)
│  ├─ Live preview
│  ├─ Focus indicator
│  └─ Flashlight toggle
│
├─ Verification Result
│  ├─ Authentic ✓ Green or Counterfeit ✗ Red
│  ├─ Product details
│  ├─ Confidence score
│  └─ Report button (if counterfeit)
│
├─ Report Counterfeit
│  ├─ Product location (auto-detected)
│  ├─ Photo capture/upload
│  ├─ Additional notes
│  ├─ Contact info (pre-filled)
│  └─ Submit button
│
├─ History
│  ├─ Timeline of all verifications
│  ├─ Filter by date/status
│  └─ Detail view per item
│
└─ Settings
   ├─ Language selection (English, Swahili, French)
   ├─ Notification preferences
   ├─ Offline mode status
   ├─ Cache management
   ├─ About & Help
   └─ Logout
```

### 1.8 Build & Release

```bash
# Sign release APK
keytool -genkey -v -keystore agrishield-signing.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias agrishield

# Build release APK
./gradlew assembleRelease

# Build release Bundle (Google Play)
./gradlew bundleRelease

# Upload to Google Play Console
# 1. Create app in Google Play Console (package: com.agrishield.farmer)
# 2. Upload signing key or use Play App Signing
# 3. Upload bundle/APK to Internal Testing
# 4. Promote to Alpha → Beta → Production
```

### 1.9 Testing Strategy

```kotlin
// androidTest/AuthServiceTest.kt
@RunWith(AndroidJUnit4::class)
class AuthServiceTest {
    private lateinit var api: FakeAgriShieldApi
    private lateinit var tokenStore: FakeTokenStore
    private lateinit var authService: AuthServiceImpl

    @Before
    fun setUp() {
        api = FakeAgriShieldApi()
        tokenStore = FakeTokenStore()
        authService = AuthServiceImpl(
            api = api,
            tokenStore = tokenStore,
            isOnline = { true }
        )
    }

    @Test
    fun testOtpVerification() = runTest {
        val result = authService.verifyOtp("+255700000001", "123456")
        
        assertTrue(result.isSuccess)
        val token = result.getOrNull()
        assertNotNull(token)
        assertEquals("farmer", token?.user?.role)
    }

    @Test
    fun testOfflineAuthentication() = runTest {
        authService = AuthServiceImpl(
            api = api,
            tokenStore = tokenStore,
            isOnline = { false }
        )
        
        val result = authService.verifyOtp("+255700000001", "123456")
        
        assertTrue(result.isSuccess)
        assertEquals("offline", result.getOrNull()?.user?.region)
    }
}
```

---

## Part 2: iOS App Specification

### 2.1 Project Setup (SwiftUI)

```swift
// AgriShield.xcodeproj/project.pbxproj
// Target: iOS 14+
// Language: Swift 5.9
// UI: SwiftUI (No UIKit)

// Package.swift (Dependencies)
let package = Package(
    name: "AgriShield",
    dependencies: [
        .package(url: "https://github.com/Alamofire/Alamofire.git", from: "5.8.0"),
        .package(url: "https://github.com/realm/realm-swift.git", from: "10.45.0"),
        .package(url: "https://github.com/firebase/firebase-ios-sdk.git", from: "10.18.0"),
        .package(url: "https://github.com/pointfreeco/swift-dependencies.git", from: "1.1.0"),
    ]
)

// Podfile
target 'AgriShield' do
  pod 'Alamofire', '~> 5.8'
  pod 'RealmSwift', '~> 10.45'
  pod 'FirebaseAuth'
  pod 'GoogleSignIn', '~> 7.0'
  pod 'KeychainAccess'
  pod 'Kingfisher', '~> 7.0'
end
```

### 2.2 Authentication (SMS OTP)

```swift
// Models/Auth.swift
struct UserProfile: Codable, Identifiable {
    let id: String
    let name: String
    let phone: String
    let role: String  // "farmer"
    let region: String
}

struct AuthToken: Codable {
    let accessToken: String
    let refreshToken: String
    let expiresAt: Date
    let user: UserProfile
}

// Services/AuthService.swift
protocol AuthServiceProtocol {
    func requestOTP(phoneNumber: String) async throws -> OTPResponse
    func verifyOTP(phoneNumber: String, code: String) async throws -> AuthToken
    func logout() async throws
}

class AuthService: AuthServiceProtocol {
    @ObservedRealmObject private var secureStorage = SecureTokenStorage()
    
    private let baseURL = "https://Agrishield.com"
    private var isOnline: Bool {
        NetworkMonitor.shared.isConnected
    }
    
    func requestOTP(phoneNumber: String) async throws -> OTPResponse {
        guard isOnline else {
            throw AuthError.offline("Cannot request OTP while offline")
        }
        
        let endpoint = "\(baseURL)/api/auth/password/forgot"
        var request = URLRequest(url: URL(string: endpoint)!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(["identifier": phoneNumber])
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw AuthError.requestFailed("Failed to request OTP")
        }
        
        return try JSONDecoder().decode(OTPResponse.self, from: data)
    }
    
    func verifyOTP(phoneNumber: String, code: String) async throws -> AuthToken {
        if !isOnline {
            // Demo offline mode
            let localToken = AuthToken(
                accessToken: "local_\(Date().timeIntervalSince1970)",
                refreshToken: "",
                expiresAt: Date(timeIntervalSinceNow: 7 * 24 * 60 * 60),
                user: UserProfile(
                    id: "demo_\(phoneNumber.hashCode)",
                    name: "Farmer (Offline)",
                    phone: phoneNumber,
                    role: "farmer",
                    region: "offline"
                )
            )
            try await secureStorage.saveToken(localToken)
            return localToken
        }
        
        let endpoint = "\(baseURL)/api/auth/password/reset"
        var request = URLRequest(url: URL(string: endpoint)!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode([
            "code": code,
            "newPassword": "\(code)Temp#\(phoneNumber.suffix(4))"
        ])
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw AuthError.verificationFailed("Invalid OTP code")
        }
        
        let token = try JSONDecoder().decode(AuthToken.self, from: data)
        try await secureStorage.saveToken(token)
        return token
    }
    
    func logout() async throws {
        try await secureStorage.clearToken()
    }
}

enum AuthError: LocalizedError {
    case offline(String)
    case requestFailed(String)
    case verificationFailed(String)
    
    var errorDescription: String? {
        switch self {
        case .offline(let msg): return msg
        case .requestFailed(let msg): return msg
        case .verificationFailed(let msg): return msg
        }
    }
}

// Secure Storage
@MainActor
class SecureTokenStorage: ObservableObject {
    @Published var token: AuthToken?
    
    private let keychain = KeychainAccess.Keychain(service: "com.agrishield.farmer")
    
    func saveToken(_ token: AuthToken) async throws {
        let data = try JSONEncoder().encode(token)
        let json = String(data: data, encoding: .utf8) ?? ""
        try keychain.set(json, key: "auth_token")
        await MainActor.run { self.token = token }
    }
    
    func loadToken() async throws -> AuthToken? {
        guard let json = try keychain.get("auth_token") else { return nil }
        let data = json.data(using: .utf8) ?? Data()
        return try JSONDecoder().decode(AuthToken.self, from: data)
    }
    
    func clearToken() async throws {
        try keychain.remove("auth_token")
        await MainActor.run { self.token = nil }
    }
}
```

### 2.3 QR Scanning (Vision Framework)

```swift
// Views/ScannerView.swift
import AVFoundation
import Vision
import CoreML

struct ScannerView: UIViewControllerRepresentable {
    var onScanSuccess: (String) -> Void
    var onScanError: (String) -> Void
    
    func makeUIViewController(context: Context) -> ScannerViewController {
        return ScannerViewController(
            onScanSuccess: onScanSuccess,
            onScanError: onScanError
        )
    }
    
    func updateUIViewController(_ uiViewController: ScannerViewController, context: Context) {}
}

class ScannerViewController: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate {
    var onScanSuccess: (String) -> Void
    var onScanError: (String) -> Void
    
    private let session = AVCaptureSession()
    private let previewLayer = AVCaptureVideoPreviewLayer()
    private var requests = [VNRequest]()
    
    init(onScanSuccess: @escaping (String) -> Void, 
         onScanError: @escaping (String) -> Void) {
        self.onScanSuccess = onScanSuccess
        self.onScanError = onScanError
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupCameraSession()
        setupVisionRequest()
    }
    
    private func setupCameraSession() {
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            onScanError("Camera not available")
            return
        }
        
        guard let input = try? AVCaptureDeviceInput(device: device) else {
            onScanError("Cannot create camera input")
            return
        }
        
        session.addInput(input)
        
        let output = AVCaptureVideoDataOutput()
        output.setSampleBufferDelegate(self, queue: DispatchQueue(label: "qr.scan.queue"))
        session.addOutput(output)
        
        previewLayer.session = session
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)
        
        DispatchQueue.main.async {
            self.previewLayer.frame = self.view.bounds
        }
        
        DispatchQueue.global(qos: .background).async {
            self.session.startRunning()
        }
    }
    
    private func setupVisionRequest() {
        let barcodeRequest = VNDetectBarcodesRequest(completionHandler: handleBarcodesDetection)
        barcodeRequest.symbologies = [.QR]
        requests = [barcodeRequest]
    }
    
    private func handleBarcodesDetection(request: VNRequest, error: Error?) {
        guard let results = request.results as? [VNBarcodeObservation] else {
            return
        }
        
        for barcode in results {
            guard let payload = barcode.payloadStringValue else { continue }
            
            DispatchQueue.main.async {
                if let code = self.extractCode(from: payload) {
                    self.onScanSuccess(code)
                }
            }
        }
    }
    
    private func extractCode(from payload: String) -> String? {
        guard let url = URLComponents(string: payload) else { return nil }
        return url.queryItems?.first(where: { $0.name == "code" })?.value
    }
    
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        
        let imageRequestHandler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, options: [:])
        try? imageRequestHandler.perform(requests)
    }
}

// SwiftUI Wrapper
struct QRScannerScreen: View {
    var onScanSuccess: (String) -> Void
    var onScanError: (String) -> Void
    
    var body: some View {
        ZStack {
            ScannerView(onScanSuccess: onScanSuccess, onScanError: onScanError)
                .ignoresSafeArea()
            
            VStack {
                HStack {
                    Button(action: {}) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.white)
                    }
                    .padding()
                    Spacer()
                }
                
                Spacer()
                
                // QR scan overlay
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.green, lineWidth: 3)
                    .frame(width: 200, height: 200)
                
                Spacer()
            }
            .padding()
        }
    }
}
```

### 2.4 Verification & Offline Sync

```swift
// Services/VerificationService.swift
@MainActor
class VerificationService: ObservableObject {
    @Published var verifications: [VerificationResult] = []
    @Published var syncState: SyncState = .idle
    
    private let database = RealmDatabase()
    private let networkMonitor = NetworkMonitor.shared
    private let baseURL = "https://Agrishield.com"
    
    func verifyQRCode(_ code: String) async -> VerificationResult {
        // Check local cache first
        if let cached = database.getVerification(code: code) {
            return cached
        }
        
        guard networkMonitor.isConnected else {
            return VerificationResult(
                code: code,
                productName: "Unknown (Offline)",
                manufacturer: "",
                batchNumber: "",
                expiryDate: "",
                isAuthentic: false,
                confidence: 0.0,
                region: "offline"
            )
        }
        
        do {
            let result = try await fetchVerification(code: code)
            database.saveVerification(result)
            verifications.append(result)
            return result
        } catch {
            return VerificationResult(
                code: code,
                productName: "Verification Failed",
                manufacturer: error.localizedDescription,
                batchNumber: "",
                expiryDate: "",
                isAuthentic: false,
                confidence: 0.0,
                region: ""
            )
        }
    }
    
    private func fetchVerification(code: String) async throws -> VerificationResult {
        let endpoint = URL(string: "\(baseURL)/api/verify?code=\(code)")!
        let (data, response) = try await URLSession.shared.data(from: endpoint)
        
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw NSError(domain: "VerificationError", code: -1)
        }
        
        return try JSONDecoder().decode(VerificationResult.self, from: data)
    }
    
    func reportCounterfeit(_ report: CounterfeitReport) async {
        syncState = .syncing
        
        if networkMonitor.isConnected {
            do {
                try await submitReport(report)
                syncState = .success
            } catch {
                database.queueReport(report)
                syncState = .offline
            }
        } else {
            database.queueReport(report)
            syncState = .offline
        }
    }
    
    private func submitReport(_ report: CounterfeitReport) async throws {
        let endpoint = URL(string: "\(baseURL)/api/verify/report")!
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(report)
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw NSError(domain: "SubmissionError", code: -1)
        }
    }
    
    func syncPendingReports() async {
        guard networkMonitor.isConnected else { return }
        
        syncState = .syncing
        let pending = database.getPendingReports()
        
        for report in pending {
            do {
                try await submitReport(report)
                database.markReportSynced(report.id)
            } catch {
                syncState = .error(error.localizedDescription)
                return
            }
        }
        
        syncState = .success
    }
}

enum SyncState {
    case idle
    case syncing
    case success
    case offline
    case error(String)
}

// Database models
struct VerificationResult: Codable, Identifiable {
    let id: String = UUID().uuidString
    let code: String
    let productName: String
    let manufacturer: String
    let batchNumber: String
    let expiryDate: String
    let isAuthentic: Bool
    let confidence: Float
    let checkedAt: Date = Date()
    let region: String
}

struct CounterfeitReport: Codable, Identifiable {
    let id: String = UUID().uuidString
    let verificationCode: String
    let location: CLLocationCoordinate2D
    let phoneNumber: String
    let notes: String
    let photos: [Data]
    let createdAt: Date = Date()
}
```

### 2.5 SwiftUI Screens

```swift
// Views/Dashboard
struct DashboardView: View {
    @StateObject private var authService: AuthService
    @StateObject private var verificationService: VerificationService
    @State private var showScanner = false
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                // Header
                HStack {
                    Text("AgriShield")
                        .font(.system(size: 24, weight: .bold))
                    Spacer()
                    Button(action: { authService.logout() }) {
                        Image(systemName: "square.and.arrow.up")
                    }
                }
                .padding()
                
                // Verification button
                Button(action: { showScanner = true }) {
                    Label("Scan QR Code", systemImage: "qrcode.viewfinder")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.green)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
                .padding()
                
                // Recent verifications
                Text("Recent Scans")
                    .font(.headline)
                    .padding(.horizontal)
                
                ForEach(verificationService.verifications.prefix(5)) { result in
                    VerificationCard(result: result)
                }
                
                Spacer()
            }
            .sheet(isPresented: $showScanner) {
                QRScannerScreen(
                    onScanSuccess: { code in
                        Task {
                            let result = await verificationService.verifyQRCode(code)
                            showScanner = false
                            // Navigate to result screen
                        }
                    },
                    onScanError: { error in
                        // Show error
                    }
                )
            }
        }
    }
}

struct VerificationCard: View {
    let result: VerificationResult
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(result.productName)
                    .font(.headline)
                Spacer()
                Image(systemName: result.isAuthentic ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .foregroundColor(result.isAuthentic ? .green : .red)
            }
            
            Text(result.manufacturer)
                .font(.subheadline)
                .foregroundColor(.gray)
            
            HStack {
                Text("Batch: \(result.batchNumber)")
                Spacer()
                Text("\(Int(result.confidence * 100))% confident")
            }
            .font(.caption)
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(8)
        .padding(.horizontal)
    }
}

// Authentication screen
struct AuthenticationView: View {
    @StateObject private var authService: AuthService
    @State private var phoneNumber = ""
    @State private var otp = ""
    @State private var showOTPEntry = false
    
    var body: some View {
        VStack(spacing: 20) {
            Text("AgriShield")
                .font(.system(size: 32, weight: .bold))
            
            Text("Farmer Input Verification")
                .foregroundColor(.gray)
            
            if !showOTPEntry {
                // Phone input
                TextField("Phone Number", text: $phoneNumber)
                    .textContentType(.telephoneNumber)
                    .keyboardType(.phonePad)
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(8)
                
                Button(action: {
                    Task {
                        try? await authService.requestOTP(phoneNumber: phoneNumber)
                        showOTPEntry = true
                    }
                }) {
                    Text("Request OTP")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
            } else {
                // OTP input
                TextField("OTP Code", text: $otp)
                    .keyboardType(.numberPad)
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(8)
                
                Button(action: {
                    Task {
                        try? await authService.verifyOTP(phoneNumber: phoneNumber, code: otp)
                    }
                }) {
                    Text("Verify")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.green)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
            }
            
            Spacer()
        }
        .padding()
    }
}
```

### 2.6 Testing

```swift
// Tests/AuthServiceTests.swift
import XCTest

@MainActor
final class AuthServiceTests: XCTestCase {
    var authService: AuthService!
    
    override func setUp() {
        super.setUp()
        authService = AuthService()
    }
    
    func testOTPVerification() async throws {
        let token = try await authService.verifyOTP(
            phoneNumber: "+255700000001",
            code: "123456"
        )
        
        XCTAssertEqual(token.user.role, "farmer")
        XCTAssertEqual(token.user.phone, "+255700000001")
    }
    
    func testOfflineMode() async throws {
        // Mock offline network
        NetworkMonitor.shared.isConnected = false
        
        let token = try await authService.verifyOTP(
            phoneNumber: "+255700000001",
            code: "123456"
        )
        
        XCTAssertEqual(token.user.region, "offline")
    }
}
```

---

## Part 3: Shared Requirements & Deployment

### 3.1 API Contracts

#### Request Password Reset (OTP Request)
```
POST /api/auth/password/forgot
{
  "identifier": "+255700000001"
}

Response: 200 OK
{
  "accepted": true,
  "channel": "sms",
  "message": "OTP sent successfully"
}
```

#### Verify OTP (Password Reset)
```
POST /api/auth/password/reset
{
  "code": "123456",
  "newPassword": "TempPass#123456"
}

Response: 200 OK
{
  "success": true,
  "accessToken": "jwt_token_here",
  "refreshToken": "refresh_token",
  "expiresAt": 1704067200000,
  "user": {
    "id": "farmer_001",
    "name": "John Farmer",
    "phone": "+255700000001",
    "role": "farmer",
    "region": "Arusha"
  }
}
```

#### Verify Product
```
GET /api/verify?code=QR_CODE_VALUE

Response: 200 OK
{
  "code": "QR_CODE_VALUE",
  "productName": "Phosphate Fertilizer 46% NPK",
  "manufacturer": "Yara East Africa",
  "batchNumber": "YEA-2025-001234",
  "expiryDate": "2027-12-31",
  "isAuthentic": true,
  "confidence": 0.98,
  "region": "Tanzania"
}
```

#### Report Counterfeit
```
POST /api/verify/report
{
  "verificationCode": "QR_CODE_VALUE",
  "location": {
    "latitude": -3.3869,
    "longitude": 36.6830
  },
  "phoneNumber": "+255700000001",
  "notes": "Found at local market, packaging damaged",
  "photos": ["base64_image_data..."]
}

Response: 201 Created
{
  "reportId": "REP_001",
  "received": true
}
```

### 3.2 Google Play & App Store Release

**Android (Google Play Console):**
```
1. Create app in Google Play Console
2. Upload signed APK/Bundle
3. Fill app details:
   - Title: AgriShield Farmer
   - Description: Verify agricultural inputs with QR codes
   - Screenshots: 5 min (feature, scanner, result, history, settings)
   - Privacy Policy: Required
   - Permissions: Camera, Location, Contacts (for SMS)
4. Set pricing: Free
5. Content rating: Questionnaire (PEGI 3 or lower)
6. Test on internal testing track first
7. Promote to Beta → Alpha → Production
```

**iOS (App Store Connect):**
```
1. Register app in App Store Connect
2. Create app signing certificates
3. Upload build (Xcode or Application Loader)
4. Fill app information:
   - Privacy Policy: Required
   - Screenshots: 5 min + iPad screenshots
   - Promotional Description
5. Configure app permissions (NSCameraUsageDescription, NSLocationWhenInUseUsageDescription)
6. Submit for review (24-48 hour typical review time)
7. Promote to public release
```

### 3.3 Localization

**Languages:**
- English (en)
- Swahili (sw)
- French (fr)

**Key Strings (iOS - Localizable.strings):**
```
"scanner.title" = "Scan QR Code";
"scanner.invalid" = "Invalid QR code format";
"result.authentic" = "Product is Authentic";
"result.counterfeit" = "Counterfeit Product Detected";
"auth.phone" = "Enter phone number";
"auth.otp" = "Enter 6-digit OTP";
"sync.offline" = "Offline Mode - Sync when online";
"sync.syncing" = "Syncing...";
```

### 3.4 Security Checklist

- [ ] SMS OTP verification implemented with rate limiting
- [ ] Token storage encrypted (EncryptedSharedPreferences / Keychain)
- [ ] API calls use HTTPS only
- [ ] Sensitive data not logged (tokens, PII)
- [ ] Certificate pinning for API domain
- [ ] App signing certificates securely stored
- [ ] Biometric authentication supported (optional)
- [ ] Offline data encrypted locally

### 3.5 Performance Checklist

- [ ] QR scanning latency < 1 second
- [ ] API response time < 3 seconds
- [ ] App cold start < 2 seconds
- [ ] Cache hit rate > 80% for repeat scans
- [ ] Memory usage < 150MB
- [ ] Battery drain < 5% per hour of active use

---

## Part 4: Testing & QA

### Test Plan

| Feature | Test Case | Expected Result |
|---------|-----------|-----------------|
| SMS OTP | Send OTP to +255700000001 | SMS received within 10 sec |
| Verify OTP | Enter valid 6-digit code | Token issued, redirected to dashboard |
| Scan QR | Point camera at QR code | Code detected within 1 sec, redirected to result |
| Product Authentic | Scan authentic product | Green ✓, confidence > 95% |
| Product Counterfeit | Scan counterfeit product | Red ✗, confidence < 50% |
| Report Counterfeit | Fill report form + submit | Report saved locally if offline, synced on reconnect |
| Offline Mode | Disable internet, scan code | Show cached result or "offline" message |
| Sync | Enable internet after offline | Queue submitted reports sync to backend |

### Beta Testing

- [ ] Internal testing (Dev team) - 1 week
- [ ] Alpha testing (20 farmers) - 2 weeks
- [ ] Beta testing (500 farmers) - 4 weeks
- [ ] Public release

---

## Part 5: Deployment Timeline & Rollout

**Week 1-2:** Android Development & Testing
**Week 3:** iOS Development & Testing
**Week 4:** Review & Submission to App Stores
**Week 5-6:** App Store review process (may be in parallel)
**Week 7:** Beta rollout to initial farmer group (100 users)
**Week 8:** Gather feedback, bug fixes
**Week 9:** Production rollout (full release)

---

## Success Metrics

- **Adoption:** 500+ farmers using app within 3 months
- **Verification Accuracy:** 98%+ correct authentic vs counterfeit detection
- **User Satisfaction:** 4.5+ star rating on app stores
- **Offline Capability:** 95%+ of scans work in offline mode
- **API Performance:** 99.9% uptime,  < 500ms avg response time

---

## Appendix: Sample Code Files

See companion GitHub repository for:
- Complete Android project (Kotlin + Jetpack Compose)
- Complete iOS project (Swift + SwiftUI)
- Unit & integration tests
- CI/CD pipeline (GitHub Actions)
- API documentation (OpenAPI/Swagger)

**Repository:** https://github.com/your-repo/agrishield-mobile  
**Issues & Feature Requests:** GitHub Issues  
**Contributing:** See CONTRIBUTING.md
