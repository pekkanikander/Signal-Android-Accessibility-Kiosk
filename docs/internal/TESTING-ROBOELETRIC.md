

# Testing Signal‑Android components that depend on SQLCipher (Robolectric vs Instrumentation)

## TL;DR
Stop trying to load SQLCipher in Robolectric. JVM tests cannot dlopen `libsqlcipher.so`. Unit‑test your `ConversationViewModel` with **fakes** and keep **all persistence/integration tests** that touch `SignalDatabase` as **instrumented tests** (`androidTest`) on an emulator/device.

---

## Why Robolectric fails here
- Robolectric runs on the desktop JVM; native loading of SQLCipher (`libsqlcipher.so`) isn’t available.
- Even if you bypass `SqlCipherLibraryLoader.load()` in `Application.onCreate()`, any reference to `net.sqlcipher.*` later will crash.
- Treat SQLCipher as an **integration boundary**.

---

## Practical strategy for your fork

### A) ViewModel tests (fast, JVM/Robolectric) — **never touch SQLCipher**
1) **Abstract the data layer** the VM depends on:
   ```kotlin
   interface ConversationRepository {
       fun stream(threadId: Long): Flow<ConversationState>
       suspend fun send(message: OutgoingMessage)
   }
   ```
2) **Production** uses your real DB-backed impl; **tests** use a **fake**:
   ```kotlin
   class FakeConversationRepository : ConversationRepository {
       private val states = MutableStateFlow<ConversationState>(ConversationState.Empty)
       override fun stream(threadId: Long) = states
       override suspend fun send(message: OutgoingMessage) {
           // update states as needed for assertions
       }
   }
   ```
3) **Inject via your DI** (wrapper over `AppDependencies`/`SignalStore`/Hilt/Koin — whatever your fork uses). Prefer **fakes** over deep mocks to avoid MockK edge‑cases with singletons/objects.
4) **Optional shadow**: silence the initial library loader if your `Application` calls it, but **do not** call any `net.sqlcipher.*` APIs in these tests.
   ```kotlin
   @Implements(SqlCipherLibraryLoader::class)
   class ShadowSqlCipherLibraryLoader {
       @Implementation
       fun load(@Suppress("UNUSED_PARAMETER") ctx: Context) { /* no‑op */ }
   }

   @RunWith(RobolectricTestRunner::class)
   @Config(shadows = [ShadowSqlCipherLibraryLoader::class], application = TestApp::class)
   class ConversationViewModelTest {
       @get:Rule val mainDispatcherRule = MainDispatcherRule()

       @Test fun state_updates_on_send() = runTest {
           val repo = FakeConversationRepository()
           val vm = ConversationViewModel(repo, dispatcher = Dispatchers.Main)
           // assert on vm state using Turbine or your preferred tool
       }
   }
   ```

### B) DB/integration tests (slower, `androidTest`) — **real SQLCipher**
- Use instrumented tests and load SQLCipher before opening DBs:
  ```kotlin
  @RunWith(AndroidJUnit4::class)
  class SignalDatabaseIT {
      @Before fun boot() {
          net.sqlcipher.database.SQLiteDatabase.loadLibs(
              ApplicationProvider.getApplicationContext()
          )
      }

      @Test fun migration_from_X_to_Y() { /* open real DB and assert */ }
  }
  ```
- Run on emulator/device (Gradle Managed Devices works in CI). Use this suite for:
  - schema/migrations
  - DAO/repository queries
  - end‑to‑end flows that must hit storage

---

## If you *must* have JVM‑speed DB tests
Create a **test‑only no‑cipher flavour** and swap the DB provider to plain `androidx.sqlite`. Keep the public surface identical so VMs/repositories don’t care.

**Sketch:**
- `flavorDimensions "db"`
- `productFlavors { cipher { } nocipher { } }`
- In `nocipher`, bind `SignalDatabaseProvider` to a `PlainSqliteSignalDb` that never references `net.sqlcipher.*`.

Caveats: app code must not import `net.sqlcipher.*` in that flavour’s classpath or static initialisers will still break. This is a maintenance trade‑off; the VM/Instrumentation split is cleaner.

---

## Answers to your questions
- **Best practices (Robolectric + SQLCipher):** Don’t load SQLCipher. Test logic with fakes; DB with instrumented tests.
- **Existing Signal‑Android testing patterns to follow?** I don’t know.
- **Switch to instrumentation for DB‑dependent components?** Yes.
- **Mock entire DB layer without triggering SQLCipher?** Yes — via an interface + fake repo. Ensure JVM tests never reference `net.sqlcipher.*`.
- **Signal‑Android‑specific testing utilities you might be missing?** I don’t know.

---

## Action checklist for your `ConversationViewModel`
- [ ] Introduce `ConversationRepository` (or similar) boundary.
- [ ] Provide `FakeConversationRepository` for tests.
- [ ] Inject fakes via your DI in JVM tests; avoid MockK deep‑mocking of singletons.
- [ ] Add a `ShadowSqlCipherLibraryLoader` only to silence startup in Robolectric.
- [ ] Move all DB‑touching tests to `androidTest` and call `SQLiteDatabase.loadLibs()` in `@Before`.
- [ ] (Optional) Consider a `nocipher` flavour if you truly need JVM DB tests.
