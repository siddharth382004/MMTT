# SIH - Location Tracking and Communication App

## Overview

This Android application is designed for location tracking and communication with remote devices, primarily through SMS. It is built to be resilient in environments with limited or no internet connectivity by providing both online and offline map functionalities. The app can send commands to and receive location data and status alerts from configured target devices.

This project was developed as part of the Smart India Hackathon (SIH).

## Features

*   **Dual Map Support:**
    *   **Offline Mode:** Utilizes the Mapsforge library to render maps from local map files, enabling navigation without an internet connection.
    *   **Online Mode:** Integrates a WebView to display online maps for when internet is available.
*   **SMS-Based Communication:**
    *   Send location request commands (`#LOCATION`) to remote devices.
    *   Receive and parse SMS messages for location coordinates (`LAT`, `LON`) and status keywords (`DANGER`, `CONNECTION LOST`).
*   **Message Management:**
    *   **Categorized Inbox:** Automatically filters and displays operational messages.
    *   **SOS Alerts:** A dedicated screen to highlight and view urgent messages containing keywords like "SOS", "DANGER", or "HELP".
*   **Device Management:**
    *   Easily send commands to a pre-configured list of target devices.
*   **Local Data Persistence:**
    *   Uses Room database to store incoming SMS messages for history and offline access.
*   **Modern Android UI:**
    *   Built entirely with Jetpack Compose for a declarative and modern user interface.

## Technologies Used

*   **Language:** Kotlin
*   **UI:** Jetpack Compose
*   **Asynchronicity:** Kotlin Coroutines
*   **Database:** Room Persistence Library
*   **Offline Maps:** Mapsforge
*   **Build System:** Gradle

## Project Structure

*   `app/src/main/java/com/example/sih/ui`: Contains all the UI-related code, including Activities and Composable screens.
*   `app/src/main/java/com/example/sih/db`: Defines the Room database, entities (`SmsEntity`), and Data Access Objects (`SmsDao`).
*   `app/src/main/res`: Includes Android resources like layouts, drawables, and values.

## Setup and Installation

1.  **Clone the repository:**
    ```bash
    git clone <repository-url>
    ```
2.  **Open in Android Studio:**
    Open the cloned project folder in Android Studio.
3.  **Build the project:**
    Let Android Studio sync the Gradle files and download the necessary dependencies.
4.  **Run the application:**
    Deploy the application on an Android emulator or a physical device.

**Permissions:**
The application requires the following permissions to function correctly. You will be prompted to grant them on the first launch:
*   `SEND_SMS`: To send commands to target devices.
*   `RECEIVE_SMS`: To listen for incoming messages.
*   `READ_SMS`: To read SMS messages for processing.
*   `INTERNET`: For the online map functionality.
*   `POST_NOTIFICATIONS`: (On Android 13+).

## How It Works

The application operates based on a simple SMS command protocol.

1.  **Sending Commands:** From the "Offline" map screen, the user can pull up a control panel to send a `#LOCATION` command to one or all target devices.
2.  **Receiving Data:** The app has a broadcast receiver that listens for incoming SMS.
3.  **Parsing and Storing:** When an SMS is received, it's parsed for operational keywords. If location data (`LAT`, `LON`) is found, it's stored in the `SmsEntity` format in the local Room database.
4.  **Displaying Information:**
    *   The **Inbox** tab shows all operational messages grouped by sender.
    *   The **SOS** tab filters for and displays only emergency alerts.
    *   Users can tap a message in the inbox to view the corresponding location on the offline map.
