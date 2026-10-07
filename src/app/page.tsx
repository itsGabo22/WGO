"use client";

import { useState, useEffect } from "react";
import { ScreenListening } from "@/components/ScreenListening";
import { ScreenModeSelection } from "@/components/ScreenModeSelection";

export default function Home() {
  const [currentScreen, setCurrentScreen] = useState<"listening" | "mode">("listening");
  const [isListening, setIsListening] = useState(false);

  useEffect(() => {
    let timeout: NodeJS.Timeout;
    if (isListening) {
      // Mock processing delay of 3 seconds
      timeout = setTimeout(() => {
        setIsListening(false);
        setCurrentScreen("mode");
      }, 3000);
    }
    return () => clearTimeout(timeout);
  }, [isListening]);

  const handleToggleListen = () => {
    setIsListening(true);
  };

  const handleCancel = () => {
    setIsListening(false);
    setCurrentScreen("listening");
  };

  const handleSelectMode = (mode: "drive" | "walk") => {
    // End of prototype flow, we can reset or just alert
    console.log(`Selected mode: ${mode}`);
    // Optional: reset after selection for testing
    setCurrentScreen("listening");
  };

  return (
    <main className="flex h-full w-full bg-background relative">
      {currentScreen === "listening" ? (
        <ScreenListening 
          isListening={isListening} 
          onToggleListen={handleToggleListen} 
          onCancel={handleCancel} 
        />
      ) : (
        <ScreenModeSelection 
          onSelectMode={handleSelectMode} 
        />
      )}
    </main>
  );
}
