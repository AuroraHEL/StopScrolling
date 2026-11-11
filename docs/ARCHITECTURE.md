# Architecture

StopScrolling uses the **MVVM** pattern (Model - View - ViewModel) with Jetpack Compose and Room.

| Part | What it is | In our project |
|---|---|---|
| Model | Data and the logic to read and save it | Room database, DAOs, repositories (`data` package) |
| View | What the user sees | Compose screens (`ui` package) |
| ViewModel | Keeps the data that a screen shows and answers to the user's actions | One ViewModel for each screen (`ui` package) |

## Data flow

```
Screen  ->  ViewModel  ->  Repository  ->  DAO  ->  Room database
```

- A screen only talks to its ViewModel.
- A ViewModel only talks to a repository.
- A repository is the only class that uses the DAOs.
- We never skip a step. For example, a screen never calls a DAO directly.

## Package structure

```
com.example.stopscrolling
├── MainActivity.kt
├── data
│   ├── local               Room: entities, DAO and database
│   │   ├── AppGroup.kt
│   │   ├── GroupApp.kt
│   │   ├── AppGroupDao.kt
│   │   └── AppDatabase.kt
│   └── repository
│       └── AppGroupRepository.kt
├── ui
│   ├── theme               created by Android Studio
│   ├── navigation          NavHost and routes
│   ├── dashboard           DashboardScreen + DashboardViewModel
│   ├── permission          Usage Access permission screen (US-02)
│   ├── applist             list of installed apps (US-06)
│   └── newgroup            create a new app group (US-08)
└── util                    small helper functions
```

Later sprints will add new packages when they are needed, for example `service` (usage monitoring) and `bluetooth` (friend approval).

## Naming

- One package for each screen. Inside it: `XxxScreen.kt` (the composable) and `XxxViewModel.kt`.
- Repositories end with `Repository`, DAOs end with `Dao`.
- Class names in `UpperCamelCase`, functions and variables in `lowerCamelCase`.
- Keep functions small and with one clear job. Use `private` and `val` whenever possible.
- Git does not save empty folders. A new package appears in the repository when it contains its first file.
