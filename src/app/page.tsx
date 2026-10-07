"use client";

import { useState, useEffect } from "react";
import { ScreenListening } from "@/components/ScreenListening";
import { ScreenModeSelection } from "@/components/ScreenModeSelection";
import { ScreenActiveNavigation } from "@/components/ScreenActiveNavigation";
import { ScreenDestinationReached } from "@/components/ScreenDestinationReached";
import { GPSLostOverlay } from "@/components/GPSLostOverlay";
import { GPSDebugTrigger } from "@/components/GPSDebugTrigger";
import { motion, AnimatePresence } from "framer-motion";

export enum ScreenState {
  Listening = "listening",
  ModeSelection = "mode",
  Navigation = "navigation",
  Arrived = "arrived"
}

export default function Home() {
  const [currentScreen, setCurrentScreen] = useState<ScreenState>(ScreenState.Listening);
  const [isListening, setIsListening] = useState(false);
  const [gpsLost, setGpsLost] = useState(false);

  useEffect(() => {
    let timeout: NodeJS.Timeout;
    if (isListening) {
      timeout = setTimeout(() => {
        setIsListening(false);
        setCurrentScreen(ScreenState.ModeSelection);
      }, 3000);
    }
    return () => clearTimeout(timeout);
  }, [isListening]);

  const handleToggleListen = () => setIsListening(true);
  
  const handleReset = () => {
    setIsListening(false);
    setGpsLost(false);
    setCurrentScreen(ScreenState.Listening);
  };
  
  const handleSelectMode = (mode: "drive" | "walk") => {
    setCurrentScreen(ScreenState.Navigation);
  };

  const handleArrived = () => {
    setCurrentScreen(ScreenState.Arrived);
  };

  return (
    <main className="flex h-full w-full bg-background relative overflow-hidden">
      <GPSDebugTrigger 
        gpsLost={gpsLost} 
        onToggle={() => setGpsLost(!gpsLost)} 
        className="top-2 left-2" 
      />

      <AnimatePresence mode="wait">
        {currentScreen === ScreenState.Listening && (
          <motion.div 
            key="listening"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -20 }}
            transition={{ duration: 0.3 }}
            className="w-full h-full absolute inset-0"
          >
            <ScreenListening isListening={isListening} onToggleListen={handleToggleListen} onCancel={handleReset} />
          </motion.div>
        )}
        
        {currentScreen === ScreenState.ModeSelection && (
          <motion.div 
            key="mode"
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 20 }}
            transition={{ duration: 0.3 }}
            className="w-full h-full absolute inset-0"
          >
            <ScreenModeSelection onSelectMode={handleSelectMode} />
          </motion.div>
        )}

        {currentScreen === ScreenState.Navigation && (
          <motion.div 
            key="navigation"
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 20 }}
            transition={{ duration: 0.3 }}
            className="w-full h-full absolute inset-0"
          >
            <ScreenActiveNavigation 
              isActive={!gpsLost} 
              onCancel={handleReset} 
              onArrived={handleArrived} 
            />
            <GPSLostOverlay isVisible={gpsLost} />
          </motion.div>
        )}

        {currentScreen === ScreenState.Arrived && (
          <motion.div 
            key="arrived"
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            transition={{ duration: 0.3 }}
            className="w-full h-full absolute inset-0"
          >
            <ScreenDestinationReached onDismiss={handleReset} />
          </motion.div>
        )}
      </AnimatePresence>
    </main>
  );
}
