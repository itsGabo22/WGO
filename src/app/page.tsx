"use client";

import { useState, useEffect } from "react";
import { ScreenListening } from "@/components/ScreenListening";
import { ScreenModeSelection } from "@/components/ScreenModeSelection";
import { ScreenActiveNavigation } from "@/components/ScreenActiveNavigation";
import { ScreenArrived } from "@/components/ScreenArrived";
import { motion, AnimatePresence } from "framer-motion";

type ScreenState = "listening" | "mode" | "navigation" | "arrived";
type TransportMode = "drive" | "walk" | null;

export default function Home() {
  const [currentScreen, setCurrentScreen] = useState<ScreenState>("listening");
  const [isListening, setIsListening] = useState(false);
  const [transportMode, setTransportMode] = useState<TransportMode>(null);

  useEffect(() => {
    let timeout: NodeJS.Timeout;
    if (isListening) {
      timeout = setTimeout(() => {
        setIsListening(false);
        setCurrentScreen("mode");
      }, 3000);
    }
    return () => clearTimeout(timeout);
  }, [isListening]);

  const handleToggleListen = () => setIsListening(true);
  
  const handleCancel = () => {
    setIsListening(false);
    setCurrentScreen("listening");
  };
  
  const handleSelectMode = (mode: "drive" | "walk") => {
    setTransportMode(mode);
    setCurrentScreen("navigation");
  };

  const handleArrived = () => {
    setCurrentScreen("arrived");
  };

  return (
    <main className="flex h-full w-full bg-background relative overflow-hidden">
      <AnimatePresence mode="wait">
        {currentScreen === "listening" && (
          <motion.div 
            key="listening"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -20 }}
            transition={{ duration: 0.3 }}
            className="w-full h-full absolute inset-0"
          >
            <ScreenListening isListening={isListening} onToggleListen={handleToggleListen} onCancel={handleCancel} />
          </motion.div>
        )}
        
        {currentScreen === "mode" && (
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

        {currentScreen === "navigation" && (
          <motion.div 
            key="navigation"
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 20 }}
            transition={{ duration: 0.3 }}
            className="w-full h-full absolute inset-0"
          >
            <ScreenActiveNavigation 
              isActive={true} 
              onCancel={handleCancel} 
              onArrived={handleArrived} 
            />
          </motion.div>
        )}

        {currentScreen === "arrived" && (
          <motion.div 
            key="arrived"
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            transition={{ duration: 0.3 }}
            className="w-full h-full absolute inset-0"
          >
            <ScreenArrived onDismiss={handleCancel} />
          </motion.div>
        )}
      </AnimatePresence>
    </main>
  );
}
